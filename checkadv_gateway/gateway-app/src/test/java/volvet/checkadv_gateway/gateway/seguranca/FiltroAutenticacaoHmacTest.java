package volvet.checkadv_gateway.gateway.seguranca;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StreamUtils;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;

class FiltroAutenticacaoHmacTest {

    private static final long AGORA = 1792760000L;
    private static final String SEGREDO = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final AssinadorHmac ASSINADOR = AssinadorHmac.deSegredoBase64(SEGREDO);

    private final RepositorioChavesHmac chaves = keyId -> switch (keyId) {
        case "grp-A1B2" -> Optional.of(new ChaveResolvida(keyId, TipoChave.GRUPO, "A1B2", ASSINADOR));
        case "op-1" -> Optional.of(new ChaveResolvida(keyId, TipoChave.OPERADOR, null, ASSINADOR));
        default -> Optional.empty();
    };

    private final FiltroAutenticacaoHmac filtro = new FiltroAutenticacaoHmac(
            TipoChave.GRUPO, chaves, Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC),
            300, 8192, new RespostaErroJson(JsonMapper.builder().build()));

    @AfterEach
    void limpar() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void assinaturaValidaAutenticaEPreservaCorpo() throws Exception {
        byte[] corpo = "{\"contaId\":\"c-7f3a\"}".getBytes(StandardCharsets.UTF_8);
        var request = requisicao("POST", "/v1/envios", null, corpo);
        assinar(request, "grp-A1B2", AGORA, corpo);
        var chain = new MockFilterChain();
        var response = new MockHttpServletResponse();

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
        assertThat(StreamUtils.copyToByteArray(chain.getRequest().getInputStream())).isEqualTo(corpo);
        var principal = (ChaveAutenticada) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        assertThat(principal.grupoPrefixo()).isEqualTo("A1B2");
    }

    @Test
    void queryEntraNaAssinatura() throws Exception {
        var request = requisicao("GET", "/v1/envios", "contaId=c-1&referenciaExterna=com-1%231", null);
        request.addHeader(CabecalhosGateway.KEY_ID, "grp-A1B2");
        request.addHeader(CabecalhosGateway.TIMESTAMP, Long.toString(AGORA));
        request.addHeader(CabecalhosGateway.ASSINATURA, ASSINADOR.assinar(AGORA, "GET",
                "/v1/envios?contaId=c-1&referenciaExterna=com-1%231", null));
        var chain = new MockFilterChain();

        filtro.doFilter(request, new MockHttpServletResponse(), chain);

        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void corpoAlteradoDepoisDeAssinarRecusa() throws Exception {
        var request = requisicao("POST", "/v1/envios", null, "{\"a\":2}".getBytes());
        assinar(request, "grp-A1B2", AGORA, "{\"a\":1}".getBytes());

        assertRecusado(request);
    }

    @Test
    void timestampForaDaJanelaRecusa() throws Exception {
        var request = requisicao("POST", "/v1/envios", null, new byte[0]);
        assinar(request, "grp-A1B2", AGORA - 301, new byte[0]);

        assertRecusado(request);
    }

    @Test
    void chaveDeOperadorNaoServeParaV1() throws Exception {
        var request = requisicao("POST", "/v1/envios", null, new byte[0]);
        assinar(request, "op-1", AGORA, new byte[0]);

        assertRecusado(request);
    }

    @Test
    void chaveDesconhecidaOuHeadersAusentesRecusa() throws Exception {
        var semChave = requisicao("POST", "/v1/envios", null, new byte[0]);
        assinar(semChave, "grp-ZZZZ", AGORA, new byte[0]);
        assertRecusado(semChave);

        assertRecusado(requisicao("POST", "/v1/envios", null, new byte[0]));
    }

    @Test
    void corpoAcimaDoLimiteDevolve400() throws Exception {
        byte[] grande = new byte[8193];
        var request = requisicao("POST", "/v1/envios", null, grande);
        assinar(request, "grp-A1B2", AGORA, grande);
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(400);
        assertThat(response.getContentAsString()).contains("PAYLOAD_INVALIDO");
        assertThat(chain.getRequest()).isNull();
    }

    private void assertRecusado(MockHttpServletRequest request) throws Exception {
        var response = new MockHttpServletResponse();
        var chain = new MockFilterChain();

        filtro.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(401);
        assertThat(response.getContentAsString()).isEqualTo(
                "{\"codigo\":\"NAO_AUTENTICADO\",\"mensagem\":\"Não autenticado.\"}");
        assertThat(chain.getRequest()).isNull();
        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }

    private static MockHttpServletRequest requisicao(String metodo, String uri, String query, byte[] corpo) {
        var request = new MockHttpServletRequest(metodo, uri);
        request.setQueryString(query);
        if (corpo != null) {
            request.setContent(corpo);
            request.setContentType("application/json");
        }
        return request;
    }

    private static void assinar(MockHttpServletRequest request, String keyId, long ts, byte[] corpo) {
        ASSINADOR.cabecalhos(keyId, ts, request.getMethod(), request.getRequestURI(), corpo)
                .comoMapa().forEach(request::addHeader);
    }
}
