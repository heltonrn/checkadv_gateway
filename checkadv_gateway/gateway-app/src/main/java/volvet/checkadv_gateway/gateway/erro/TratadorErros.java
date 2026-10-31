package volvet.checkadv_gateway.gateway.erro;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.contrato.erro.ErroResposta;

/**
 * Converte exceções no formato de erro do contrato. Mensagens de exceções inesperadas
 * nunca vão para o cliente (podem conter dados); só para o log.
 */
@RestControllerAdvice
public class TratadorErros {

    private static final Logger log = LoggerFactory.getLogger(TratadorErros.class);

    @ExceptionHandler(ExcecaoGateway.class)
    ResponseEntity<ErroResposta> negocio(ExcecaoGateway e) {
        return ResponseEntity.status(e.status()).body(new ErroResposta(e.codigo(), e.getMessage(), e.detalhes()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ErroResposta> validacao(MethodArgumentNotValidException e) {
        Map<String, Object> campos = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors()
                .forEach(f -> campos.putIfAbsent(f.getField(), f.getDefaultMessage()));
        return invalido("Payload inválido.", Map.of("campos", campos));
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    ResponseEntity<ErroResposta> validacaoParametros(HandlerMethodValidationException e) {
        return invalido("Parâmetros inválidos.", Map.of());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ErroResposta> ilegivel(HttpMessageNotReadableException e) {
        return invalido("JSON inválido ou campos com tipo errado.", Map.of());
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingRequestHeaderException.class})
    ResponseEntity<ErroResposta> parametroInvalido(Exception e) {
        return invalido("Parâmetro ausente ou com formato inválido.", Map.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<ErroResposta> tipoMidia(HttpMediaTypeNotSupportedException e) {
        return invalido("Content-Type deve ser application/json.", Map.of());
    }

    @ExceptionHandler({NoResourceFoundException.class, HttpRequestMethodNotSupportedException.class})
    ResponseEntity<ErroResposta> naoEncontrado(Exception e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ErroResposta.de(CodigoErro.NAO_ENCONTRADO, "Recurso não encontrado."));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ErroResposta> inesperado(Exception e) {
        log.error("Erro inesperado", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ErroResposta.de(CodigoErro.ERRO_INTERNO, "Erro interno."));
    }

    private static ResponseEntity<ErroResposta> invalido(String mensagem, Map<String, Object> detalhes) {
        return ResponseEntity.badRequest().body(new ErroResposta(CodigoErro.PAYLOAD_INVALIDO, mensagem, detalhes));
    }
}
