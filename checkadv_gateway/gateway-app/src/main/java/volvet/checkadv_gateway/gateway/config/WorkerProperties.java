package volvet.checkadv_gateway.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param habilitado          liga os agendamentos (worker, varredor, purga); desligado nos testes
 * @param lote                envios reservados por ciclo
 * @param threads             chamadas simultâneas à Meta
 * @param lease               tempo máximo em ENVIANDO antes de o varredor marcar INCERTO
 * @param maxTentativas       falhas temporárias antes de FALHOU
 * @param mensagensPorSegundo limite interno por número (a Meta aceita 80/s)
 * @param politica5xx         "incerto" (padrão) ou "retry" para timeout/5xx depois de conectar
 * @param retencao            depois desse tempo em estado terminal, parâmetros (token do link) são apagados
 */
@ConfigurationProperties("app.worker")
public record WorkerProperties(
        @DefaultValue("true") boolean habilitado,
        @DefaultValue("20") int lote,
        @DefaultValue("4") int threads,
        @DefaultValue("2m") Duration lease,
        @DefaultValue("5") int maxTentativas,
        @DefaultValue("20") int mensagensPorSegundo,
        @DefaultValue("incerto") String politica5xx,
        @DefaultValue("7d") Duration retencao) {

    public boolean retentarApos5xx() {
        return "retry".equalsIgnoreCase(politica5xx);
    }
}
