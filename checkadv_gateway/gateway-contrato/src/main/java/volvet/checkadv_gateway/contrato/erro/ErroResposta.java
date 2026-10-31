package volvet.checkadv_gateway.contrato.erro;

import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Corpo padrão de erro da API v1 e da API administrativa.
 *
 * <pre>{ "codigo": "COTA_EXCEDIDA", "mensagem": "...", "detalhes": { ... } }</pre>
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErroResposta(CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {

    public static ErroResposta de(CodigoErro codigo, String mensagem) {
        return new ErroResposta(codigo, mensagem, Map.of());
    }
}
