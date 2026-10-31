package volvet.checkadv_gateway.gateway.fila;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import org.springframework.stereotype.Component;

import volvet.checkadv_gateway.gateway.config.WorkerProperties;

/**
 * Token bucket simples por {@code phone_number_id} (limite interno abaixo dos 80 msg/s da
 * Meta). Em memória, por réplica. Bucket4j com backend no banco substitui este quando
 * houver várias réplicas de worker.
 */
@Component
public class LimitadorTaxa {

    private final double porSegundo;
    private final ConcurrentHashMap<String, Balde> baldes = new ConcurrentHashMap<>();

    public LimitadorTaxa(WorkerProperties props) {
        this.porSegundo = Math.max(1, props.mensagensPorSegundo());
    }

    /** Bloqueia até haver ficha para este número. */
    public void aguardar(String phoneNumberId) throws InterruptedException {
        Balde balde = baldes.computeIfAbsent(phoneNumberId, k -> new Balde(porSegundo));
        long espera;
        while ((espera = balde.tentarConsumir()) > 0) {
            TimeUnit.NANOSECONDS.sleep(espera);
        }
    }

    private static final class Balde {
        private final double capacidade;
        private final double fichasPorNano;
        private double fichas;
        private long ultimo = System.nanoTime();

        Balde(double porSegundo) {
            this.capacidade = porSegundo;
            this.fichasPorNano = porSegundo / 1_000_000_000d;
            this.fichas = porSegundo;
        }

        /** @return 0 se consumiu; senão, nanos até a próxima ficha */
        synchronized long tentarConsumir() {
            long agora = System.nanoTime();
            fichas = Math.min(capacidade, fichas + (agora - ultimo) * fichasPorNano);
            ultimo = agora;
            if (fichas >= 1) {
                fichas -= 1;
                return 0;
            }
            return (long) Math.ceil((1 - fichas) / fichasPorNano);
        }
    }
}
