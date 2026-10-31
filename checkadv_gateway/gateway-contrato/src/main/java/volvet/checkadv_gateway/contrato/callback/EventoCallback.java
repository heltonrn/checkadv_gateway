package volvet.checkadv_gateway.contrato.callback;

import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Envelope comum de todo callback Gateway → instância.
 *
 * Regras para quem recebe: responder 2xx em até 5 s, deduplicar por {@code eventoId}
 * e, em {@code envio.status_alterado}, aplicar só se a {@code sequencia} for maior que a gravada.
 *
 * @param <D> tipo de {@code dados}, conforme {@link #tipo()}
 */
public record EventoCallback<D>(
        UUID eventoId,
        TipoEvento tipo,
        String contaId,
        OffsetDateTime ocorridoEm,
        D dados) {
}
