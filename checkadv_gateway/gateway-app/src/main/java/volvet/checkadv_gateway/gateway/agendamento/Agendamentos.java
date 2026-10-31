package volvet.checkadv_gateway.gateway.agendamento;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;

import volvet.checkadv_gateway.gateway.callback.DespachanteCallbacks;
import volvet.checkadv_gateway.gateway.config.WorkerProperties;
import volvet.checkadv_gateway.gateway.fila.WorkerEnvios;

/**
 * Tarefas periódicas. Desligadas com {@code app.worker.habilitado=false} (testes chamam os
 * ciclos diretamente). Seguras com várias réplicas: todas reservam linhas com SKIP LOCKED.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.worker.habilitado", havingValue = "true", matchIfMissing = true)
public class Agendamentos {

    private static final Logger log = LoggerFactory.getLogger(Agendamentos.class);

    private final WorkerEnvios worker;
    private final DespachanteCallbacks despachante;
    private final WorkerProperties props;

    public Agendamentos(WorkerEnvios worker, DespachanteCallbacks despachante, WorkerProperties props) {
        this.worker = worker;
        this.despachante = despachante;
        this.props = props;
    }

    /** Esvazia a fila em lotes enquanto houver trabalho, depois espera o próximo ciclo. */
    @Scheduled(fixedDelayString = "${app.worker.intervalo:1000}", initialDelay = 5000)
    void enviar() {
        executar("worker", () -> {
            while (worker.ciclo() >= props.lote()) {
                // lote cheio: provavelmente há mais na fila
            }
        });
    }

    @Scheduled(fixedDelayString = "${app.callback.intervalo:2000}", initialDelay = 5000)
    void callbacks() {
        executar("callbacks", () -> {
            while (despachante.ciclo() > 0) {
                // continua enquanto houver callbacks vencidos
            }
        });
    }

    @Scheduled(fixedDelay = 30_000, initialDelay = 30_000)
    void varrerLeases() {
        executar("varredor", worker::varrerLeasesVencidos);
    }

    @Scheduled(fixedDelay = 3_600_000, initialDelay = 60_000)
    void purgar() {
        executar("purga", () -> {
            int n = worker.purgarParametros();
            if (n > 0) {
                log.info("Parâmetros de {} envio(s) apagados (retenção)", n);
            }
        });
    }

    private void executar(String nome, Runnable tarefa) {
        try {
            tarefa.run();
        } catch (RuntimeException e) {
            log.error("Falha na tarefa agendada '{}'", nome, e);
        }
    }
}
