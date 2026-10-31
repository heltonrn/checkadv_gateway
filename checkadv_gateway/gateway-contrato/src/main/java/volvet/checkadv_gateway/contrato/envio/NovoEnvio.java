package volvet.checkadv_gateway.contrato.envio;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import volvet.checkadv_gateway.contrato.Formatos;

/**
 * Corpo de {@code POST /v1/envios}. A chave de idempotência é
 * {@code contaId + referenciaExterna}; reenvio usa nova referência (ex.: {@code com-1842#2}).
 */
public record NovoEnvio(
        @NotBlank @Pattern(regexp = Formatos.ID_OPACO) String contaId,
        @NotBlank @Pattern(regexp = Formatos.REFERENCIA_EXTERNA) String referenciaExterna,
        @NotNull @Valid Destinatario destinatario,
        @NotNull @Valid TemplateEnvio template) {
}
