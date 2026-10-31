package volvet.checkadv_gateway.gateway.config;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Chave mestra AES-256 (Base64, 32 bytes) que cifra segredos HMAC, tokens dos
 * escritórios e telefones. Vem de variável de ambiente/cofre, NUNCA do banco.
 *
 * @param chaveMestraId identificador gravado junto de cada valor cifrado (rotação)
 */
@Validated
@ConfigurationProperties("app.cripto")
public record CriptoProperties(
        @NotBlank String chaveMestraId,
        @NotBlank String chaveMestra) {

    @Override
    public String toString() {
        return "CriptoProperties[chaveMestraId=" + chaveMestraId + ", chaveMestra=***]";
    }
}
