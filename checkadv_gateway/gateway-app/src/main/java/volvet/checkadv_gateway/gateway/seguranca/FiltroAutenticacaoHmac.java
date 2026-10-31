package volvet.checkadv_gateway.gateway.seguranca;

import java.io.IOException;
import java.io.InputStream;
import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.contrato.erro.ErroResposta;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;

/**
 * Autentica requisições assinadas com HMAC (ver {@code AssinadorHmac}).
 *
 * Ordem: headers presentes → timestamp na janela → tamanho do corpo → chave ativa do
 * tipo exigido → assinatura em tempo constante. Qualquer falha de autenticação devolve
 * o MESMO 401; o motivo só vai para o log (com keyId, nunca segredo nem corpo).
 *
 * Não é um @Bean de propósito: se fosse, o Spring Boot o registraria também como filtro
 * global de servlet. É instanciado dentro de cada SecurityFilterChain.
 *
 * Limitação conhecida: a janela de ±300 s não impede replay dentro dela. As rotas que
 * alteram estado são idempotentes por contrato (referenciaExterna); se for preciso,
 * um nonce entra depois sem mudar o formato da assinatura.
 */
public class FiltroAutenticacaoHmac extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(FiltroAutenticacaoHmac.class);

    private final TipoChave tipoExigido;
    private final RepositorioChavesHmac chaves;
    private final Clock relogio;
    private final long janelaSegundos;
    private final int corpoMaximoBytes;
    private final RespostaErroJson resposta;

    public FiltroAutenticacaoHmac(TipoChave tipoExigido, RepositorioChavesHmac chaves, Clock relogio,
                                  long janelaSegundos, int corpoMaximoBytes, RespostaErroJson resposta) {
        this.tipoExigido = tipoExigido;
        this.chaves = chaves;
        this.relogio = relogio;
        this.janelaSegundos = janelaSegundos;
        this.corpoMaximoBytes = corpoMaximoBytes;
        this.resposta = resposta;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String keyId = request.getHeader(CabecalhosGateway.KEY_ID);
        String timestampTexto = request.getHeader(CabecalhosGateway.TIMESTAMP);
        String assinatura = request.getHeader(CabecalhosGateway.ASSINATURA);

        if (vazio(keyId) || vazio(timestampTexto) || vazio(assinatura)) {
            negar(response, "headers de autenticação ausentes", keyId);
            return;
        }

        long timestamp;
        try {
            timestamp = Long.parseLong(timestampTexto.trim());
        } catch (NumberFormatException e) {
            negar(response, "X-Timestamp inválido", keyId);
            return;
        }
        long agora = relogio.instant().getEpochSecond();
        if (Math.abs(agora - timestamp) > janelaSegundos) {
            negar(response, "X-Timestamp fora da janela (desvio " + (agora - timestamp) + " s)", keyId);
            return;
        }

        if (request.getContentLengthLong() > corpoMaximoBytes) {
            corpoGrande(response);
            return;
        }
        byte[] corpo = lerCorpo(request.getInputStream());
        if (corpo == null) {
            corpoGrande(response);
            return;
        }

        Optional<ChaveResolvida> chave = chaves.buscarAtiva(keyId.trim());
        if (chave.isEmpty() || chave.get().tipo() != tipoExigido) {
            negar(response, "chave inexistente, inativa ou de outro tipo", keyId);
            return;
        }

        String caminho = caminhoComQuery(request);
        if (!chave.get().assinador().confere(assinatura, timestamp, request.getMethod(), caminho, corpo)) {
            negar(response, "assinatura não confere", keyId);
            return;
        }

        ChaveResolvida c = chave.get();
        SecurityContext contexto = SecurityContextHolder.createEmptyContext();
        contexto.setAuthentication(new AutenticacaoHmac(new ChaveAutenticada(c.keyId(), c.tipo(), c.grupoPrefixo())));
        SecurityContextHolder.setContext(contexto);

        chain.doFilter(new RequisicaoComCorpo(request, corpo), response);
    }

    /** Caminho como veio na linha de requisição: sem decodificar, com "?query" se houver. */
    static String caminhoComQuery(HttpServletRequest request) {
        String query = request.getQueryString();
        return query == null || query.isEmpty() ? request.getRequestURI() : request.getRequestURI() + "?" + query;
    }

    /** Lê até o limite; devolve {@code null} se passar dele (Content-Length ausente ou mentiroso). */
    private byte[] lerCorpo(InputStream entrada) throws IOException {
        byte[] lido = entrada.readNBytes(corpoMaximoBytes + 1);
        return lido.length > corpoMaximoBytes ? null : lido;
    }

    private void negar(HttpServletResponse response, String motivo, String keyId) throws IOException {
        log.warn("Autenticação HMAC recusada ({}): {}", keyId == null ? "sem keyId" : keyId, motivo);
        resposta.naoAutenticado(response);
    }

    private void corpoGrande(HttpServletResponse response) throws IOException {
        resposta.escrever(response, HttpStatus.BAD_REQUEST, new ErroResposta(CodigoErro.PAYLOAD_INVALIDO,
                "Corpo excede o limite.", Map.of("limiteBytes", corpoMaximoBytes)));
    }

    private static boolean vazio(String valor) {
        return valor == null || valor.isBlank();
    }
}
