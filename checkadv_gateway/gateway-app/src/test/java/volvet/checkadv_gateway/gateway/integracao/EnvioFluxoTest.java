package volvet.checkadv_gateway.gateway.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import tools.jackson.databind.JsonNode;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;

@EnabledIfEnvironmentVariable(named = "GATEWAY_TEST_DB_URL", matches = ".+")
class EnvioFluxoTest extends IntegracaoBase {

    @Test
    void fluxoCompletoDoEnvioAteLidoComCallbacksAssinados() throws Exception {
        contaPronta("c-1");
        v1("PUT", "/v1/grupo/callback", "{\"url\":\"" + RECEPTOR.url("/integracao/gateway/eventos") + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(true));

        // 1. Enfileira: 202 + Location + cota restante
        var r = v1("POST", "/v1/envios", envio("c-1", "com-1842#1", "+5544999998888"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", org.hamcrest.Matchers.startsWith("/v1/envios/")))
                .andExpect(header().string(CabecalhosGateway.COTA_RESTANTE, "199"))
                .andExpect(jsonPath("$.status").value("ENFILEIRADO"))
                .andExpect(jsonPath("$.sequencia").value(0));
        String envioId = corpo(r).get("envioId").asString();

        // Telefone e token não ficam em claro no banco
        String linha = jdbc.sql("SELECT CONCAT(telefone_cifrado, parametros_json, telefone_mascarado) FROM envio_whatsapp WHERE envio_id = ?")
                .param(envioId).query(String.class).single();
        assertThat(linha).doesNotContain("5544999998888").contains("+55 44 9****-8888");

        // 2. Worker envia para a Meta (simulada) → ENVIADO
        assertThat(worker.ciclo()).isEqualTo(1);
        assertThat(statusDe(envioId)).isEqualTo("ENVIADO");

        // 3. Status assíncronos da Meta → ENTREGUE → LIDO
        await().atMost(Duration.ofSeconds(5)).until(() -> statusDe(envioId).equals("LIDO"));

        // 4. GET com histórico e cobrança
        v1("GET", "/v1/envios/" + envioId, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LIDO"))
                .andExpect(jsonPath("$.sequencia").value(3))
                .andExpect(jsonPath("$.destinatario.telefoneMascarado").value("+55 44 9****-8888"))
                .andExpect(jsonPath("$.cobranca.categoria").value("UTILITY"))
                .andExpect(jsonPath("$.cobranca.cobravel").value(true))
                .andExpect(jsonPath("$.historico.length()").value(4))
                .andExpect(jsonPath("$.historico[3].status").value("LIDO"));
        v1("GET", "/v1/envios?contaId=c-1&referenciaExterna=com-1842%231", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envioId").value(envioId));

        // 5. Callbacks: entregues à "instância", assinados com a chave do grupo
        while (despachante.ciclo() > 0) {
            // entrega tudo o que está pendente
        }
        List<ReceptorCallbacks.Recebido> recebidos = RECEPTOR.recebidos();
        List<String> tipos = recebidos.stream().map(c -> json.readTree(c.corpo()).get("tipo").asString()).toList();
        assertThat(tipos).contains("conta.whatsapp_alterada", "template.status_alterado");
        List<String> statusRecebidos = recebidos.stream().map(c -> json.readTree(c.corpo()))
                .filter(n -> n.get("tipo").asString().equals("envio.status_alterado"))
                .map(n -> n.get("dados").get("status").asString() + "#" + n.get("dados").get("sequencia").asInt())
                .toList();
        assertThat(statusRecebidos).containsExactlyInAnyOrder("ENVIADO#1", "ENTREGUE#2", "LIDO#3");

        for (ReceptorCallbacks.Recebido c : recebidos) {
            long ts = Long.parseLong(c.header(CabecalhosGateway.TIMESTAMP));
            assertThat(c.header(CabecalhosGateway.KEY_ID)).isEqualTo(grupo.keyId());
            assertThat(grupo.assinador().confere(c.header(CabecalhosGateway.ASSINATURA), ts, "POST",
                    "/integracao/gateway/eventos", c.corpo())).isTrue();
            assertThat(new String(c.corpo(), StandardCharsets.UTF_8)).doesNotContain("5544999998888");
        }
        assertThat(jdbc.sql("SELECT COUNT(*) FROM callback_pendente WHERE status <> 'ENTREGUE'")
                .query(Integer.class).single()).isZero();
    }

    @Test
    void repeticaoIdempotenteEConflito() throws Exception {
        contaPronta("c-1");
        String corpo = envio("c-1", "r-1", "+5544999998888");
        String id = corpo(v1("POST", "/v1/envios", corpo).andExpect(status().isAccepted())).get("envioId").asString();

        v1("POST", "/v1/envios", corpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.envioId").value(id));
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999997777"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("CONFLITO_IDEMPOTENCIA"));
        assertThat(usadoHoje("c-1")).isEqualTo(1);
    }

    @Test
    void cancelaSoEnquantoEnfileirado() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999998888");

        v1("POST", "/v1/envios/" + id + "/cancelar", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELADO"))
                .andExpect(jsonPath("$.sequencia").value(1));
        assertThat(usadoHoje("c-1")).isZero();

        v1("POST", "/v1/envios/" + id + "/cancelar", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.codigo").value("ESTADO_INVALIDO"));
        assertThat(worker.ciclo()).isZero();
    }

    @Test
    void regrasDeNegocioAntesDeEnfileirar() throws Exception {
        registrarConta("c-1");
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTA_SEM_WHATSAPP"));

        conectarWhatsapp("c-1", "1464632673389713");
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888", token(OUTRO_GRUPO)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detalhes.campos['template.botaoUrl']").exists());

        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888").replace("pt_BR", "en_US"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("TEMPLATE_INDISPONIVEL"));

        v1("PUT", "/v1/contas/c-1", "{\"nomeExibicao\":\"Silva\",\"ativa\":false}");
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("CONTA_INATIVA"));

        chamar(outroGrupo, "POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888", token(OUTRO_GRUPO)))
                .andExpect(status().isNotFound());
        assertThat(usadoHoje("c-1")).isZero();
    }

    @Test
    void envioDeOutroGrupoNaoEhVisivel() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999998888");
        chamar(outroGrupo, "GET", "/v1/envios/" + id, null).andExpect(status().isNotFound());
        chamar(outroGrupo, "POST", "/v1/envios/" + id + "/cancelar", null).andExpect(status().isNotFound());
    }

    @Test
    void desconectarCancelaAFilaEBloqueiaNovosEnvios() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999998888");

        v1("DELETE", "/v1/contas/c-1/whatsapp", null).andExpect(status().isAccepted());

        assertThat(statusDe(id)).isEqualTo("CANCELADO");
        assertThat(usadoHoje("c-1")).isZero();
        v1("GET", "/v1/contas/c-1", null).andExpect(jsonPath("$.whatsapp.status").value("DESCONECTADA"));
        v1("POST", "/v1/envios", envio("c-1", "r-2", "+5544999998888"))
                .andExpect(jsonPath("$.codigo").value("CONTA_SEM_WHATSAPP"));
        JsonNode token = json.readTree(jdbc.sql("SELECT JSON_OBJECT('t', token_cifrado) FROM whatsapp_conta_config WHERE conta_id = 'c-1'")
                .query(String.class).single());
        assertThat(token.get("t").isNull()).isTrue();
    }
}
