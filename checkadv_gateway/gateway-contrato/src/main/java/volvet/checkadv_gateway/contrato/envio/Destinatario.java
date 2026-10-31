package volvet.checkadv_gateway.contrato.envio;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

import volvet.checkadv_gateway.contrato.Formatos;

/** Destinatário em E.164 (ex.: +5544999998888). Nunca é devolvido completo. */
public record Destinatario(
        @NotBlank
        @Pattern(regexp = Formatos.TELEFONE_E164, message = "telefone deve estar em E.164")
        String telefone) {

    @Override
    public String toString() {
        return "Destinatario[telefone=***]";
    }
}
