package volvet.checkadv_gateway.gateway.admin;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Corpo de {@code PATCH /admin/grupos/{prefixo}}: só os campos informados mudam.
 * Desativar faz o Resolvedor tratar os links do grupo como inválidos em até 60 s.
 */
public record AlteracaoGrupo(
        Boolean ativo,
        @Size(max = 255) @Pattern(regexp = "^https?://.+") String baseUrlInterna) {
}
