package volvet.checkadv_gateway.contrato.callback;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PUT /v1/grupo/callback}. Em produção só HTTPS (HTTP apenas se o Gateway
 * estiver configurado para desenvolvimento); o host precisa estar na lista do grupo.
 */
public record RegistroCallback(
        @NotBlank @Size(max = 500) @Pattern(regexp = "^https?://.+") String url) {
}
