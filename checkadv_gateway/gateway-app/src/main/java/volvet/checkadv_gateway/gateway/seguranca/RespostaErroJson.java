package volvet.checkadv_gateway.gateway.seguranca;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.contrato.erro.ErroResposta;

/** Escreve {@link ErroResposta} direto na resposta, para uso nos filtros de segurança. */
public class RespostaErroJson {

    private final JsonMapper mapper;

    public RespostaErroJson(JsonMapper mapper) {
        this.mapper = mapper;
    }

    public void escrever(HttpServletResponse response, HttpStatus status, ErroResposta erro) throws IOException {
        if (response.isCommitted()) {
            return;
        }
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getOutputStream().write(mapper.writeValueAsBytes(erro));
    }

    /** 401 sempre igual, sem motivo: não ajuda quem está testando chaves. */
    public void naoAutenticado(HttpServletResponse response) throws IOException {
        escrever(response, HttpStatus.UNAUTHORIZED, ErroResposta.de(CodigoErro.NAO_AUTENTICADO, "Não autenticado."));
    }

    public void semPermissao(HttpServletResponse response) throws IOException {
        escrever(response, HttpStatus.FORBIDDEN, ErroResposta.de(CodigoErro.SEM_PERMISSAO, "Sem permissão."));
    }
}
