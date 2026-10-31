package volvet.checkadv_gateway.gateway.envio;

import java.util.List;

/** Conteúdo de {@code parametros_json} (gravado cifrado): variáveis do corpo e sufixo do botão. */
public record ParametrosTemplate(List<String> corpo, String botaoUrl) {

    @Override
    public String toString() {
        return "ParametrosTemplate[corpo=" + corpo + ", botaoUrl=***]";
    }
}
