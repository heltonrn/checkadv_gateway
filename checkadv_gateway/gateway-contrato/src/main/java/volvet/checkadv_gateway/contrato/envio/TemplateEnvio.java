package volvet.checkadv_gateway.contrato.envio;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import volvet.checkadv_gateway.contrato.link.FormatoTokenLink;

/**
 * Template a enviar.
 *
 * @param corpo    valores de {{1}}, {{2}}, ... na ordem; cada um até 60 caracteres
 * @param botaoUrl sufixo variável do botão de URL (token do link, 26 caracteres).
 *                 É credencial: nunca registrar em log.
 */
public record TemplateEnvio(
        @NotBlank @Pattern(regexp = "^[a-z0-9_]{1,128}$") String nome,
        @NotBlank @Pattern(regexp = "^[a-z]{2,3}(_[A-Z]{2})?$") String idioma,
        @NotEmpty @Size(max = 10) List<@NotBlank @Size(max = 60) String> corpo,
        @NotBlank @Pattern(regexp = FormatoTokenLink.REGEX_TOKEN) String botaoUrl) {

    @Override
    public String toString() {
        return "TemplateEnvio[nome=" + nome + ", idioma=" + idioma + ", corpo=" + corpo + ", botaoUrl=***]";
    }
}
