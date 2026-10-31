package volvet.checkadv_gateway.gateway.admin;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import volvet.checkadv_gateway.contrato.link.FormatoTokenLink;

/**
 * Corpo de {@code POST /admin/grupos}. DTOs do admin ficam no gateway-app, não no
 * contrato: só o operador Volvet usa, as instâncias nunca.
 *
 * @param baseUrlInterna          para onde o Resolvedor repassa os links do grupo
 * @param hostsCallbackPermitidos hosts aceitos em PUT /v1/grupo/callback (anti-SSRF)
 */
public record NovoGrupo(
        @NotBlank @Pattern(regexp = FormatoTokenLink.REGEX_PREFIXO) String prefixo,
        @NotBlank @Size(max = 100) String nome,
        @NotBlank @Size(max = 255) @Pattern(regexp = "^https?://.+") String baseUrlInterna,
        @NotEmpty List<@NotBlank @Size(max = 255) String> hostsCallbackPermitidos) {
}
