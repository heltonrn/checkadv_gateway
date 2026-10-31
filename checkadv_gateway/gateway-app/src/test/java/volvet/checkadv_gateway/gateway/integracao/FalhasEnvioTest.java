package volvet.checkadv_gateway.gateway.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

/** Caminhos de erro do worker, provocados pelo final do telefone na Meta simulada. */
@EnabledIfEnvironmentVariable(named = "GATEWAY_TEST_DB_URL", matches = ".+")
class FalhasEnvioTest extends IntegracaoBase {

    @Test
    void falhaDefinitivaVaiParaFalhouEDevolveACota() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999990000");
        assertThat(usadoHoje("c-1")).isEqualTo(1);

        worker.ciclo();

        assertThat(statusDe(id)).isEqualTo("FALHOU");
        assertThat(usadoHoje("c-1")).isZero();
        v1("GET", "/v1/envios/" + id, null)
                .andExpect(jsonPath("$.erro.categoria").value("NUMERO_SEM_WHATSAPP"))
                .andExpect(jsonPath("$.erro.codigoMeta").value("131026"));
    }

    @Test
    void falhaTemporariaVoltaParaAFilaComEspera() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999990001");

        worker.ciclo();

        assertThat(statusDe(id)).isEqualTo("ENFILEIRADO");
        assertThat(jdbc.sql("SELECT tentativas FROM envio_whatsapp WHERE envio_id = ?").param(id)
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(worker.ciclo()).as("só volta depois da espera").isZero();
        assertThat(usadoHoje("c-1")).isEqualTo(1);
    }

    @Test
    void tentativasEsgotadasViramFalhou() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999990001");
        for (int i = 0; i < 5; i++) {
            jdbc.sql("UPDATE envio_whatsapp SET proxima_tentativa = '2020-01-01 00:00:00' WHERE envio_id = ?").param(id).update();
            worker.ciclo();
        }
        assertThat(statusDe(id)).isEqualTo("FALHOU");
        v1("GET", "/v1/envios/" + id, null).andExpect(jsonPath("$.erro.categoria").value("LIMITE_META_EXCEDIDO"));
    }

    @Test
    void respostaIncertaViraIncertoEContinuaContandoNaCota() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999990002");

        worker.ciclo();

        assertThat(statusDe(id)).isEqualTo("INCERTO");
        assertThat(usadoHoje("c-1")).isEqualTo(1);
        v1("GET", "/v1/envios/" + id, null).andExpect(jsonPath("$.erro.categoria").value("ENVIO_INCERTO"));
    }

    @Test
    void falhaInformadaDepoisPelaMetaViraFalhou() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999990003");

        worker.ciclo();
        assertThat(statusDe(id)).isEqualTo("ENVIADO");

        await().atMost(Duration.ofSeconds(5)).until(() -> statusDe(id).equals("FALHOU"));
        v1("GET", "/v1/envios/" + id, null).andExpect(jsonPath("$.erro.codigoMeta").value("131049"));
    }

    @Test
    void leaseVencidoViraIncerto() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999998888");
        jdbc.sql("UPDATE envio_whatsapp SET status = 'ENVIANDO', lease_ate = '2020-01-01 00:00:00' WHERE envio_id = ?")
                .param(id).update();

        assertThat(worker.varrerLeasesVencidos()).isEqualTo(1);

        assertThat(statusDe(id)).isEqualTo("INCERTO");
    }

    @Test
    void purgaApagaParametrosDeEnviosAntigos() throws Exception {
        contaPronta("c-1");
        String id = enfileirar("c-1", "r-1", "+5544999998888");
        worker.ciclo();
        jdbc.sql("UPDATE envio_whatsapp SET atualizado_em = '2020-01-01 00:00:00' WHERE envio_id = ?").param(id).update();

        assertThat(worker.purgarParametros()).isEqualTo(1);

        assertThat(jdbc.sql("SELECT parametros_json IS NULL FROM envio_whatsapp WHERE envio_id = ?").param(id)
                .query(Boolean.class).single()).isTrue();
    }

    @Test
    void callbackComFalhaEhRetentadoDepois() throws Exception {
        contaPronta("c-1");
        v1("PUT", "/v1/grupo/callback", "{\"url\":\"" + RECEPTOR.url("/eventos") + "\"}");
        RECEPTOR.responderCom(500);

        despachante.ciclo();

        assertThat(jdbc.sql("SELECT COUNT(*) FROM callback_pendente WHERE status = 'PENDENTE' AND tentativas = 1")
                .query(Integer.class).single()).isPositive();
        assertThat(despachante.ciclo()).as("só tenta de novo depois do backoff").isZero();
    }

    @Test
    void callbackParaHostNaoPermitidoEhRecusado() throws Exception {
        v1("PUT", "/v1/grupo/callback", "{\"url\":\"https://169.254.169.254/latest/meta-data\"}")
                .andExpect(status().isBadRequest());
    }
}
