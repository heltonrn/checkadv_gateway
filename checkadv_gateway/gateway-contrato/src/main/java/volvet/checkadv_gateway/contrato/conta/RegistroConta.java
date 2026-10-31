package volvet.checkadv_gateway.contrato.conta;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corpo de {@code PUT /v1/contas/{contaId}}. */
public record RegistroConta(
        @NotBlank @Size(max = 120) String nomeExibicao,
        @NotNull Boolean ativa) {
}
