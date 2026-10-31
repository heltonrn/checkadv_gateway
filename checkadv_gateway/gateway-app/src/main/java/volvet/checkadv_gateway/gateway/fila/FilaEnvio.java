package volvet.checkadv_gateway.gateway.fila;

import java.util.List;

/**
 * Fila de envios. Implementação atual: o próprio banco (outbox). Atrás desta interface
 * para que um broker possa substituí-la no futuro sem mexer no worker.
 */
public interface FilaEnvio {

    /**
     * Reserva até {@code lote} envios prontos (ENFILEIRADO com hora vencida), marcando-os
     * ENVIANDO com lease. Seguro com várias réplicas: cada envio vai para uma só.
     */
    List<String> reservar(int lote);
}
