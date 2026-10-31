package volvet.checkadv_gateway.gateway.integracao;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

@EnabledIfEnvironmentVariable(named = "GATEWAY_TEST_DB_URL", matches = ".+")
class ContasECotaTest extends IntegracaoBase {

    @Test
    void registraAtualizaEConsultaConta() throws Exception {
        v1("PUT", "/v1/contas/c-1", "{\"nomeExibicao\":\"Silva\",\"ativa\":true}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.contaId").value("c-1"))
                .andExpect(jsonPath("$.whatsapp.status").value("NAO_CONECTADA"))
                .andExpect(jsonPath("$.cota.restanteDia").value(200));

        v1("PUT", "/v1/contas/c-1", "{\"nomeExibicao\":\"Silva & Associados\",\"ativa\":false}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ativa").value(false));

        v1("GET", "/v1/contas/c-1", null).andExpect(status().isOk());
    }

    @Test
    void contaDeOutroGrupoNaoPodeSerTomadaNemVista() throws Exception {
        registrarConta("c-1");

        chamar(outroGrupo, "PUT", "/v1/contas/c-1", "{\"nomeExibicao\":\"Invasor\",\"ativa\":true}")
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.codigo").value("SEM_PERMISSAO"));
        chamar(outroGrupo, "GET", "/v1/contas/c-1", null)
                .andExpect(status().isNotFound());
    }

    @Test
    void contaInexistenteDevolve404() throws Exception {
        v1("GET", "/v1/contas/nao-existe", null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.codigo").value("NAO_ENCONTRADO"));
    }

    @Test
    void defineCotaEConsultaUso() throws Exception {
        registrarConta("c-1");
        String cota = """
                {"periodoInicio":"2020-01-01","periodoFim":"2099-12-31","limite":1000,"limiteDiario":50,
                 "politicaExcesso":"BLOQUEAR"}""";

        v1("PUT", "/v1/contas/c-1/cota", cota)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.limite").value(1000))
                .andExpect(jsonPath("$.restante").value(1000))
                .andExpect(jsonPath("$.diario.limite").value(50));

        v1("GET", "/v1/contas/c-1/uso", null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.periodo.inicio").value("2020-01-01"))
                .andExpect(jsonPath("$.unicos24h").value(0));
    }

    @Test
    void cotaComFimAntesDoInicioEhRecusada() throws Exception {
        registrarConta("c-1");
        v1("PUT", "/v1/contas/c-1/cota", """
                {"periodoInicio":"2026-10-31","periodoFim":"2026-10-01","limite":1,"limiteDiario":1,
                 "politicaExcesso":"BLOQUEAR"}""")
                .andExpect(status().isBadRequest());
    }

    @Test
    void cotaDoPeriodoBloqueiaOuCobraExtraConformeAPolitica() throws Exception {
        contaPronta("c-1");
        v1("PUT", "/v1/contas/c-1/cota", """
                {"periodoInicio":"2020-01-01","periodoFim":"2099-12-31","limite":1,"limiteDiario":10,
                 "politicaExcesso":"BLOQUEAR"}""");

        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999990010")).andExpect(status().isAccepted());
        v1("POST", "/v1/envios", envio("c-1", "r-2", "+5544999990011"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.codigo").value("COTA_EXCEDIDA"))
                .andExpect(jsonPath("$.detalhes.tipo").value("MENSAL"));

        // A reserva diária do envio recusado foi desfeita junto (mesma transação)
        assertThat(usadoHoje("c-1")).isEqualTo(1);

        v1("PUT", "/v1/contas/c-1/cota", """
                {"periodoInicio":"2020-01-01","periodoFim":"2099-12-31","limite":1,"limiteDiario":10,
                 "politicaExcesso":"PERMITIR_COBRAR_EXTRA"}""");
        v1("POST", "/v1/envios", envio("c-1", "r-2", "+5544999990011")).andExpect(status().isAccepted());
        v1("GET", "/v1/contas/c-1/uso", null)
                .andExpect(jsonPath("$.usado").value(2))
                .andExpect(jsonPath("$.excedente").value(1))
                .andExpect(jsonPath("$.unicos24h").value(2));
    }

    @Test
    void limiteDiarioPadraoEhAplicadoSemCotaDefinida() throws Exception {
        contaPronta("c-1");
        jdbc.sql("UPDATE cota_uso SET usado = 200 WHERE conta_id = 'c-1'").update();
        v1("POST", "/v1/envios", envio("c-1", "r-1", "+5544999990010")); // cria a janela, se não existir
        jdbc.sql("UPDATE cota_uso SET usado = limite WHERE conta_id = 'c-1' AND escopo = 'DIARIA'").update();

        v1("POST", "/v1/envios", envio("c-1", "r-2", "+5544999990011"))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.detalhes.tipo").value("DIARIA"))
                .andExpect(jsonPath("$.detalhes.limite").value(200));
    }
}
