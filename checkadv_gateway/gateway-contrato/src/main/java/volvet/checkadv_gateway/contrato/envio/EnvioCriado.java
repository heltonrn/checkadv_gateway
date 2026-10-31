package volvet.checkadv_gateway.contrato.envio;

import java.time.OffsetDateTime;
import java.util.UUID;

/** Resposta de {@code POST /v1/envios} (202 novo, 200 repetição idempotente). */
public record EnvioCriado(
        UUID envioId,
        String referenciaExterna,
        StatusEnvio status,
        long sequencia,
        OffsetDateTime criadoEm) {
}
