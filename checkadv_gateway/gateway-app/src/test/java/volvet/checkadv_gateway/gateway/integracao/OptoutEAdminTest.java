package volvet.checkadv_gateway.gateway.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;

import tools.jackson.databind.JsonNode;
import volvet.checkadv_gateway.contrato.optout.OrigemOptout;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.optout.ServicoOptout;

@EnabledIfEnvironmentVariable(named = "GATEWAY_TEST_DB_URL", matches = ".+")
class OptoutEAdminTest extends IntegracaoBase {

    @Autowired
    ServicoOptout optout;

    @Test
    void optoutBloqueiaEnvioERemocaoExigeEvidencia() throws Exception {
        contaPronta("c-1");
        v1("PUT", "/v1/grupo/callback", "{\"url\":\"" + RECEPTOR.url("/eventos") + "\"}");
        optout.registrar(GRUPO, "c-1", "+5544999998888", OrigemOptout.PALAVRA_CHAVE);

        v1("POST", "/v1/contas/c-1/optouts/consulta", "{\"telefone\":\"+5544999998888\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.optout").value(true))
                .andExpect(jsonPath("$.origem").value("PALAVRA_CHAVE"));
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.codigo").value("NUMERO_OPT_OUT"));

        String remocao = """
                {"telefone":"+5544999998888","evidencia":{"tipo":"CONSENTIMENTO_ESCRITO",
                 "registradoPor":"pessoa-42","registradoEm":"2026-10-23T09:00:00-03:00"}}""";
        v1("POST", "/v1/contas/c-1/optouts/remocao", remocao).andExpect(status().isNoContent());
        v1("POST", "/v1/contas/c-1/optouts/remocao", remocao).andExpect(status().isNotFound());
        v1("POST", "/v1/contas/c-1/optouts/consulta", "{\"telefone\":\"+5544999998888\"}")
                .andExpect(jsonPath("$.optout").value(false));
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999998888")).andExpect(status().isAccepted());

        assertThat(jdbc.sql("SELECT COUNT(*) FROM callback_pendente WHERE tipo = 'optout.registrado'")
                .query(Integer.class).single()).isEqualTo(1);
        assertThat(jdbc.sql("SELECT COUNT(*) FROM auditoria WHERE acao = 'OPTOUT_REMOVIDO'")
                .query(Integer.class).single()).isEqualTo(1);
    }

    @Test
    void operadorCriaGrupoEAChaveNovaFuncionaNaV1() throws Exception {
        JsonNode criado = corpo(admin("POST", "/admin/grupos", """
                {"prefixo":"Z9y8","nome":"Grupo 009","baseUrlInterna":"http://instancia-g009:8080",
                 "hostsCallbackPermitidos":["instancia-g009"]}""").andExpect(status().isCreated()));
        String keyId = criado.get("chave").get("keyId").asString();
        String segredo = criado.get("chave").get("segredo").asString();
        assertThat(keyId).startsWith("grp-Z9y8-");

        Credencial nova = new Credencial(keyId, AssinadorHmac.deSegredoBase64(segredo));
        chamar(nova, "PUT", "/v1/contas/c-9", "{\"nomeExibicao\":\"Lima\",\"ativa\":true}")
                .andExpect(status().isCreated());

        admin("POST", "/admin/grupos", """
                {"prefixo":"Z9y8","nome":"Dup","baseUrlInterna":"http://x:1","hostsCallbackPermitidos":["x"]}""")
                .andExpect(status().isConflict());
        admin("GET", "/admin/grupos", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.prefixo == 'Z9y8')].quantidadeContas").value(org.hamcrest.Matchers.contains(1)));
    }

    @Test
    void rotacaoDeChaveSemDeixarOGrupoSemAcesso() throws Exception {
        admin("DELETE", "/admin/grupos/" + GRUPO + "/chaves/" + grupo.keyId(), null)
                .andExpect(status().isConflict());

        JsonNode nova = corpo(admin("POST", "/admin/grupos/" + GRUPO + "/chaves", null).andExpect(status().isCreated()));
        Credencial cNova = new Credencial(nova.get("keyId").asString(),
                AssinadorHmac.deSegredoBase64(nova.get("segredo").asString()));

        admin("DELETE", "/admin/grupos/" + GRUPO + "/chaves/" + grupo.keyId(), null).andExpect(status().isNoContent());

        v1("GET", "/v1/grupo/callback", null).andExpect(status().isUnauthorized());
        chamar(cNova, "GET", "/v1/grupo/callback", null).andExpect(status().isOk());
    }

    @Test
    void desativarGrupoEDiagnosticoDaConta() throws Exception {
        contaPronta("c-1");
        enfileirar("c-1", "r-1", "+5544999990000");
        worker.ciclo();

        admin("GET", "/admin/contas/c-1/diagnostico", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.whatsapp").value("CONECTADA"))
                .andExpect(jsonPath("$.tokenValido").value(true))
                .andExpect(jsonPath("$.filaPorStatus.FALHOU").value(1))
                .andExpect(jsonPath("$.ultimosErrosMeta[0]").value(org.hamcrest.Matchers.startsWith("131026")));

        admin("PATCH", "/admin/grupos/" + GRUPO, "{\"ativo\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativo").value(false));
    }

    @Test
    void reenviaCallbackQueFalhou() throws Exception {
        contaPronta("c-1");
        String eventoId = jdbc.sql("SELECT evento_id FROM callback_pendente LIMIT 1").query(String.class).single();
        admin("POST", "/admin/callbacks/" + eventoId + "/reenviar", null).andExpect(status().isConflict());

        jdbc.sql("UPDATE callback_pendente SET status = 'CALLBACK_FALHOU' WHERE evento_id = ?").param(eventoId).update();
        admin("POST", "/admin/callbacks/" + eventoId + "/reenviar", null).andExpect(status().isAccepted());

        assertThat(jdbc.sql("SELECT status FROM callback_pendente WHERE evento_id = ?").param(eventoId)
                .query(String.class).single()).isEqualTo("PENDENTE");
    }

    @Test
    void chavesNaoSeMisturamEntreV1EAdmin() throws Exception {
        v1("GET", "/admin/grupos", null).andExpect(status().isUnauthorized());
        admin("GET", "/v1/grupo/callback", null).andExpect(status().isUnauthorized());
    }
}
