package volvet.checkadv_gateway.gateway.seguranca;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import jakarta.validation.Valid;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.envio.NovoEnvio;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;

/** Cadeia /v1 completa: filtro HMAC + autorização + validação + formato de erro. */
@WebMvcTest(controllers = SegurancaApiV1Test.ControladorTeste.class)
@Import({SecurityConfig.class, SegurancaApiV1Test.ControladorTeste.class})
class SegurancaApiV1Test {

    private static final long AGORA = 1792760000L;
    private static final AssinadorHmac ASSINADOR =
            AssinadorHmac.deSegredoBase64("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");

    @Autowired
    MockMvc mvc;

    @Test
    void requisicaoAssinadaChegaAoControllerComOGrupo() throws Exception {
        mvc.perform(assinado("POST", "/v1/teste/eco", "{\"x\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.grupo").value("A1B2"))
                .andExpect(header().exists(CabecalhosGateway.CORRELATION_ID));
    }

    @Test
    void semAssinaturaDevolve401NoFormatoDoContrato() throws Exception {
        mvc.perform(post("/v1/teste/eco").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
    }

    @Test
    void payloadInvalidoDevolve400ComCampos() throws Exception {
        String corpo = """
                {"contaId":"c-1","referenciaExterna":"com 1",
                 "destinatario":{"telefone":"44999998888"},
                 "template":{"nome":"comunicacao_registrada","idioma":"pt_BR",
                             "corpo":["Silva & Associados","K7M2-9QXH"],"botaoUrl":"curto"}}
                """;
        mvc.perform(assinado("POST", "/v1/teste/envio", corpo))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAYLOAD_INVALIDO"))
                .andExpect(jsonPath("$.detalhes.campos['destinatario.telefone']").exists())
                .andExpect(jsonPath("$.detalhes.campos['template.botaoUrl']").exists())
                .andExpect(jsonPath("$.detalhes.campos.referenciaExterna").exists());
    }

    @Test
    void rotaInexistenteAutenticadaDevolve404() throws Exception {
        mvc.perform(assinado("GET", "/v1/nao-existe", ""))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void rotaForaDasCadeiasEhNegada() throws Exception {
        mvc.perform(get("/qualquer")).andExpect(status().isUnauthorized());
    }

    private static MockHttpServletRequestBuilder assinado(String metodo, String caminho, String corpo) {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        var cabecalhos = ASSINADOR.cabecalhos("grp-A1B2", AGORA, metodo, caminho, bytes);
        var builder = request(HttpMethod.valueOf(metodo), caminho)
                .contentType(MediaType.APPLICATION_JSON).content(bytes);
        cabecalhos.comoMapa().forEach(builder::header);
        return builder;
    }

    @TestConfiguration
    static class Config {
        @Bean
        Clock relogio() {
            return Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC);
        }

        @Bean
        RepositorioChavesHmac chaves() {
            return keyId -> "grp-A1B2".equals(keyId)
                    ? Optional.of(new ChaveResolvida(keyId, TipoChave.GRUPO, "A1B2", ASSINADOR))
                    : Optional.empty();
        }
    }

    @RestController
    @RequestMapping("/v1/teste")
    static class ControladorTeste {

        record Eco(String grupo, String keyId) {
        }

        @PostMapping("/eco")
        Eco eco(@AuthenticationPrincipal ChaveAutenticada chave, @RequestBody String corpo) {
            return new Eco(chave.grupoPrefixo(), chave.keyId());
        }

        @PostMapping("/envio")
        String envio(@Valid @RequestBody NovoEnvio envio) {
            return "ok";
        }
    }
}
