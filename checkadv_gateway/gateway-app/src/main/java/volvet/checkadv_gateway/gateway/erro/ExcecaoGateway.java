package volvet.checkadv_gateway.gateway.erro;

import java.util.Map;

import org.springframework.http.HttpStatus;

import volvet.checkadv_gateway.contrato.erro.CodigoErro;

/** Erro de negócio que vira {@code ErroResposta} com o status indicado. */
public class ExcecaoGateway extends RuntimeException {

    private final HttpStatus status;
    private final CodigoErro codigo;
    private final Map<String, Object> detalhes;

    public ExcecaoGateway(HttpStatus status, CodigoErro codigo, String mensagem, Map<String, Object> detalhes) {
        super(mensagem);
        this.status = status;
        this.codigo = codigo;
        this.detalhes = detalhes == null ? Map.of() : Map.copyOf(detalhes);
    }

    public ExcecaoGateway(HttpStatus status, CodigoErro codigo, String mensagem) {
        this(status, codigo, mensagem, Map.of());
    }

    public static ExcecaoGateway naoEncontrado(String mensagem) {
        return new ExcecaoGateway(HttpStatus.NOT_FOUND, CodigoErro.NAO_ENCONTRADO, mensagem);
    }

    /**
     * Rota prevista no contrato e já mapeada, ainda sem regra de negócio.
     * Some do código à medida que cada endpoint for implementado.
     */
    public static ExcecaoGateway naoImplementado(String operacao) {
        return new ExcecaoGateway(HttpStatus.NOT_IMPLEMENTED, CodigoErro.NAO_IMPLEMENTADO,
                "Ainda não implementado: " + operacao + ".");
    }

    public static ExcecaoGateway semPermissao() {
        return new ExcecaoGateway(HttpStatus.FORBIDDEN, CodigoErro.SEM_PERMISSAO, "Sem permissão.");
    }

    public HttpStatus status() {
        return status;
    }

    public CodigoErro codigo() {
        return codigo;
    }

    public Map<String, Object> detalhes() {
        return detalhes;
    }
}
