package volvet.checkadv_gateway.contrato.envio;

import java.time.OffsetDateTime;

public record HistoricoStatus(StatusEnvio status, OffsetDateTime em) {
}
