package volvet.checkadv_gateway.contrato.callback;

import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;

/** {@code dados} de {@code conta.whatsapp_alterada}. */
public record DadosContaWhatsapp(StatusWhatsapp status, String qualidade, String limiteMensagens, String motivo) {
}
