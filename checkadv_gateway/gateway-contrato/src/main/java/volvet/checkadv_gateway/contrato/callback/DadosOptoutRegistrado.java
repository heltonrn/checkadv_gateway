package volvet.checkadv_gateway.contrato.callback;

import java.time.OffsetDateTime;

/** {@code dados} de {@code optout.registrado}. */
public record DadosOptoutRegistrado(String telefoneMascarado, String origem, OffsetDateTime registradoEm) {
}
