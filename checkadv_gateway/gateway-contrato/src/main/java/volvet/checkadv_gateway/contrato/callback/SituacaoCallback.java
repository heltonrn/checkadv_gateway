package volvet.checkadv_gateway.contrato.callback;

import java.time.OffsetDateTime;

/** Resposta de {@code GET/PUT /v1/grupo/callback}. */
public record SituacaoCallback(String url, boolean ativo, OffsetDateTime ultimaEntregaEm) {
}
