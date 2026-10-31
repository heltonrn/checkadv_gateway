package volvet.checkadv_gateway.gateway.admin;

import java.util.List;
import java.util.Map;

import volvet.checkadv_gateway.contrato.conta.StatusTemplate;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;

/**
 * Resposta de {@code GET /admin/contas/{contaId}/diagnostico}.
 *
 * @param tokenValido diz se o token decifra e é aceito pela Meta, sem nunca mostrá-lo
 */
public record DiagnosticoConta(
        String contaId,
        String grupoPrefixo,
        StatusWhatsapp whatsapp,
        boolean tokenValido,
        StatusTemplate template,
        String qualidade,
        String limiteMensagens,
        Map<StatusEnvio, Long> filaPorStatus,
        List<String> ultimosErrosMeta) {
}
