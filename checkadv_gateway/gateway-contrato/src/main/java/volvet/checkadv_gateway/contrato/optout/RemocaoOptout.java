package volvet.checkadv_gateway.contrato.optout;

import java.time.OffsetDateTime;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import volvet.checkadv_gateway.contrato.Formatos;

/**
 * Corpo de {@code POST /v1/contas/{contaId}/optouts/remocao}. Só com novo consentimento
 * do cliente, registrado pelo escritório; a remoção fica auditada no Gateway.
 */
public record RemocaoOptout(
        @NotBlank @Pattern(regexp = Formatos.TELEFONE_E164) String telefone,
        @NotNull @Valid Evidencia evidencia) {

    /** @param registradoPor identificador opaco da pessoa no CheckADV (ex.: pessoa-42) */
    public record Evidencia(
            @NotNull TipoEvidencia tipo,
            @NotBlank @Size(max = 64) String registradoPor,
            @NotNull OffsetDateTime registradoEm) {
    }

    @Override
    public String toString() {
        return "RemocaoOptout[telefone=***, evidencia=" + evidencia + "]";
    }
}
