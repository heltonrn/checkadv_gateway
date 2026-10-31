package volvet.checkadv_gateway.contrato.optout;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import volvet.checkadv_gateway.contrato.Formatos;

/** Corpo de {@code POST /v1/contas/{contaId}/optouts/consulta}. Telefone no corpo, nunca na URL. */
public record ConsultaOptout(
        @NotBlank @Pattern(regexp = Formatos.TELEFONE_E164) String telefone) {

    @Override
    public String toString() {
        return "ConsultaOptout[telefone=***]";
    }
}
