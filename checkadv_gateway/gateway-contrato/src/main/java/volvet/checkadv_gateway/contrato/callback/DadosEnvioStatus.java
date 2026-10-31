package volvet.checkadv_gateway.contrato.callback;

import java.util.UUID;

import volvet.checkadv_gateway.contrato.envio.Cobranca;
import volvet.checkadv_gateway.contrato.envio.ErroEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;

/** {@code dados} de {@code envio.status_alterado}. */
public record DadosEnvioStatus(
        UUID envioId,
        String referenciaExterna,
        StatusEnvio status,
        long sequencia,
        ErroEnvio erro,
        Cobranca cobranca) {
}
