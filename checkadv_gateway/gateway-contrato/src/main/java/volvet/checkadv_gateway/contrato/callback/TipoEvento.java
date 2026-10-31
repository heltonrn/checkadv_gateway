package volvet.checkadv_gateway.contrato.callback;

import java.util.Arrays;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/** Valor do campo {@code tipo} do envelope de callback. */
public enum TipoEvento {
    ENVIO_STATUS_ALTERADO("envio.status_alterado"),
    COTA_ALERTA("cota.alerta"),
    OPTOUT_REGISTRADO("optout.registrado"),
    CONTA_WHATSAPP_ALTERADA("conta.whatsapp_alterada"),
    TEMPLATE_STATUS_ALTERADO("template.status_alterado"),
    TESTE_PING("teste.ping");

    private final String codigo;

    TipoEvento(String codigo) {
        this.codigo = codigo;
    }

    @JsonValue
    public String codigo() {
        return codigo;
    }

    @JsonCreator
    public static TipoEvento deCodigo(String codigo) {
        return Arrays.stream(values())
                .filter(t -> t.codigo.equals(codigo))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Tipo de evento desconhecido: " + codigo));
    }
}
