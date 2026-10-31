package volvet.checkadv_gateway.gateway;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import volvet.checkadv_gateway.contrato.envio.EnvioCriado;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;
import volvet.checkadv_gateway.gateway.admin.AdminService;
import volvet.checkadv_gateway.gateway.conta.ContaService;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaService;
import volvet.checkadv_gateway.gateway.cota.ServicoCota;
import volvet.checkadv_gateway.gateway.envio.EnvioService;
import volvet.checkadv_gateway.gateway.grupo.GrupoService;
import volvet.checkadv_gateway.gateway.optout.ServicoOptout;
import volvet.checkadv_gateway.gateway.seguranca.ChaveResolvida;
import volvet.checkadv_gateway.gateway.seguranca.RepositorioChavesHmac;
import volvet.checkadv_gateway.gateway.seguranca.SecurityConfig;
import volvet.checkadv_gateway.gateway.seguranca.TipoChave;

/**
 * Camada web isolada (sem banco): cadeias de segurança, validação de entrada e formato
 * das respostas. As regras de negócio estão cobertas pelos testes de integração.
 */
@WebMvcTest
@Import(SecurityConfig.class)
class RotasContratoTest {

    private static final long AGORA = 1792760000L;
    private static final AssinadorHmac ASSINADOR =
            AssinadorHmac.deSegredoBase64("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=");
    private static final String GRUPO = "grp-A1B2";
    private static final String OPERADOR = "op-1";

    @Autowired
    MockMvc mvc;

    @MockitoBean
    EnvioService envios;
    @MockitoBean
    ContaService contas;
    @MockitoBean
    WhatsappContaService whatsapp;
    @MockitoBean
    ServicoCota cota;
    @MockitoBean
    ServicoOptout optout;
    @MockitoBean
    GrupoService grupos;
    @MockitoBean
    AdminService admin;

    private static final String ENVIO = """
            {"contaId":"c-7f3a","referenciaExterna":"com-1842#1",
             "destinatario":{"telefone":"+5544999998888"},
             "template":{"nome":"comunicacao_registrada","idioma":"pt_BR",
                         "corpo":["Silva & Associados","K7M2-9QXH"],
                         "botaoUrl":"A1B2q8Zr0kT3vYw9LmNpXc4dEe"}}""";

    @Test
    void envioNovoDevolve202ComLocationECota() throws Exception {
        UUID id = UUID.randomUUID();
        when(envios.enfileirar(eq("A1B2"), any())).thenReturn(new EnvioService.Resultado(
                new EnvioCriado(id, "com-1842#1", StatusEnvio.ENFILEIRADO, 0, OffsetDateTime.now()), true, 187));

        chamar("POST", "/v1/envios", ENVIO, GRUPO)
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/v1/envios/" + id))
                .andExpect(header().string(CabecalhosGateway.COTA_RESTANTE, "187"))
                .andExpect(jsonPath("$.status").value("ENFILEIRADO"));
    }

    @Test
    void repeticaoIdempotenteDevolve200() throws Exception {
        when(envios.enfileirar(eq("A1B2"), any())).thenReturn(new EnvioService.Resultado(
                new EnvioCriado(UUID.randomUUID(), "com-1842#1", StatusEnvio.ENVIADO, 1, OffsetDateTime.now()), false, null));

        chamar("POST", "/v1/envios", ENVIO, GRUPO)
                .andExpect(status().isOk())
                .andExpect(header().doesNotExist(CabecalhosGateway.COTA_RESTANTE));
    }

    @Test
    void semAssinaturaOuChaveErradaDevolve401() throws Exception {
        mvc.perform(request(HttpMethod.POST, URI.create("/v1/envios")).contentType(MediaType.APPLICATION_JSON).content(ENVIO))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.codigo").value("NAO_AUTENTICADO"));
        chamar("GET", "/admin/grupos", "", GRUPO).andExpect(status().isUnauthorized());
        chamar("GET", "/v1/contas/c-1", "", OPERADOR).andExpect(status().isUnauthorized());
    }

    @Test
    void corpoInvalidoListaOsCampos() throws Exception {
        chamar("POST", "/v1/envios", ENVIO.replace("+5544999998888", "44999998888").replace("com-1842#1", "com 1"), GRUPO)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.codigo").value("PAYLOAD_INVALIDO"))
                .andExpect(jsonPath("$.detalhes.campos['destinatario.telefone']").exists())
                .andExpect(jsonPath("$.detalhes.campos.referenciaExterna").exists());
    }

    @Test
    void parametrosDeRotaInvalidosDevolvem400() throws Exception {
        chamar("GET", "/v1/contas/c$1", "", GRUPO).andExpect(status().isBadRequest());
        chamar("GET", "/v1/envios/nao-e-uuid", "", GRUPO).andExpect(status().isBadRequest());
        chamar("GET", "/v1/envios?contaId=c-1", "", GRUPO).andExpect(status().isBadRequest());
        chamar("PATCH", "/admin/grupos/A1B=", "{\"ativo\":false}", OPERADOR).andExpect(status().isBadRequest());
    }

    @Test
    void webhookDaMetaAindaNaoImplementado() throws Exception {
        mvc.perform(request(HttpMethod.GET, URI.create("/webhook/meta?hub.mode=subscribe&hub.verify_token=x&hub.challenge=1")))
                .andExpect(status().isNotImplemented())
                .andExpect(jsonPath("$.codigo").value("NAO_IMPLEMENTADO"));
    }

    @Test
    void rotaDesconhecidaDevolve404NoFormatoDoContrato() throws Exception {
        chamar("GET", "/v1/nao-existe", "", GRUPO)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    private ResultActions chamar(String metodo, String caminho, String corpo, String keyId) throws Exception {
        byte[] bytes = corpo.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(metodo), URI.create(caminho));
        if (bytes.length > 0) {
            req.contentType(MediaType.APPLICATION_JSON).content(bytes);
        }
        ASSINADOR.cabecalhos(keyId, AGORA, metodo, caminho, bytes).comoMapa().forEach(req::header);
        return mvc.perform(req);
    }

    @TestConfiguration
    static class Config {
        @Bean
        Clock relogio() {
            return Clock.fixed(Instant.ofEpochSecond(AGORA), ZoneOffset.UTC);
        }

        @Bean
        RepositorioChavesHmac chaves() {
            return keyId -> switch (keyId) {
                case GRUPO -> Optional.of(new ChaveResolvida(keyId, TipoChave.GRUPO, "A1B2", ASSINADOR));
                case OPERADOR -> Optional.of(new ChaveResolvida(keyId, TipoChave.OPERADOR, null, ASSINADOR));
                default -> Optional.empty();
            };
        }
    }
}
