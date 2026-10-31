package volvet.checkadv_gateway.contrato.callback;

import volvet.checkadv_gateway.contrato.conta.StatusTemplate;

/** {@code dados} de {@code template.status_alterado}. */
public record DadosTemplateStatus(String nome, String idioma, StatusTemplate status, String motivo) {
}
