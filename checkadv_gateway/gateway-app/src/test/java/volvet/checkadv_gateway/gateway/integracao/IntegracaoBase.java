package volvet.checkadv_gateway.gateway.integracao;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.callback.DespachanteCallbacks;
import volvet.checkadv_gateway.gateway.fila.WorkerEnvios;
import volvet.checkadv_gateway.gateway.seguranca.ChavesGrupo;
import volvet.checkadv_gateway.gateway.seguranca.TipoChave;

/**
 * Base dos testes de integração: aplicação inteira + banco MySQL/MariaDB de verdade.
 *
 * Só rodam se {@code GATEWAY_TEST_DB_URL} estiver definida (ex.:
 * {@code jdbc:mysql://localhost:3306/checkadv_gateway_teste?serverTimezone=UTC}), mais
 * {@code GATEWAY_TEST_DB_USUARIO} e {@code GATEWAY_TEST_DB_SENHA}. Use um banco SÓ para
 * testes: as tabelas são esvaziadas antes de cada teste.
 *
 * O worker e o despachante não rodam sozinhos aqui ({@code app.worker.habilitado=false});
 * os testes chamam {@code worker.ciclo()} e {@code despachante.ciclo()} quando querem.
 */
@SpringBootTest(properties = {
        "app.cripto.chave-mestra=AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=",
        "app.cripto.chave-mestra-id=k1",
        "app.worker.habilitado=false",
        "app.callback.permitir-http=true",
        "app.meta.modo=simulado",
        "app.meta.simulado.atraso-entrega=200ms",
        "app.meta.simulado.atraso-leitura=400ms"})
@AutoConfigureMockMvc
abstract class IntegracaoBase {

    static final String GRUPO = "A1B2";
    static final String OUTRO_GRUPO = "B2C3";
    private static final SecureRandom RANDOM = new SecureRandom();
    static final ReceptorCallbacks RECEPTOR;

    static {
        try {
            RECEPTOR = new ReceptorCallbacks();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void banco(DynamicPropertyRegistry r) {
        r.add("spring.datasource.url", () -> System.getenv("GATEWAY_TEST_DB_URL"));
        r.add("spring.datasource.username", () -> env("GATEWAY_TEST_DB_USUARIO", "root"));
        r.add("spring.datasource.password", () -> env("GATEWAY_TEST_DB_SENHA", ""));
    }

    @Autowired
    MockMvc mvc;
    @Autowired
    JdbcClient jdbc;
    @Autowired
    ChavesGrupo chaves;
    @Autowired
    WorkerEnvios worker;
    @Autowired
    DespachanteCallbacks despachante;
    @Autowired
    JsonMapper json;

    record Credencial(String keyId, AssinadorHmac assinador) {
    }

    Credencial grupo;
    Credencial outroGrupo;
    Credencial operador;

    @BeforeEach
    void prepararBanco() {
        for (String tabela : List.of("auditoria", "optout", "callback_pendente", "envio_historico", "envio_whatsapp",
                "cota_uso", "cota_config", "whatsapp_conta_config", "conta", "chave_hmac", "grupo_instancia")) {
            jdbc.sql("DELETE FROM " + tabela).update();
        }
        inserirGrupo(GRUPO);
        inserirGrupo(OUTRO_GRUPO);
        grupo = credencial(chaves.gerar(TipoChave.GRUPO, GRUPO, "grp-" + GRUPO + "-teste"));
        outroGrupo = credencial(chaves.gerar(TipoChave.GRUPO, OUTRO_GRUPO, "grp-" + OUTRO_GRUPO + "-teste"));
        operador = credencial(chaves.gerar(TipoChave.OPERADOR, null, "op-teste"));
        RECEPTOR.limpar();
    }

    private void inserirGrupo(String prefixo) {
        jdbc.sql("""
                INSERT INTO grupo_instancia (prefixo, nome, base_url_interna, hosts_callback_permitidos, ativo, criado_em, atualizado_em)
                VALUES (?, ?, 'http://localhost:8080', 'localhost', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3))""")
                .params(prefixo, "Grupo " + prefixo).update();
    }

    private static Credencial credencial(ChavesGrupo.ChaveNova c) {
        return new Credencial(c.keyId(), AssinadorHmac.deSegredoBase64(c.segredoBase64()));
    }

    // ---------- chamadas assinadas ----------

    ResultActions chamar(Credencial cred, String metodo, String caminho, String corpo) throws Exception {
        byte[] bytes = corpo == null ? new byte[0] : corpo.getBytes(StandardCharsets.UTF_8);
        MockHttpServletRequestBuilder req = request(HttpMethod.valueOf(metodo), URI.create(caminho));
        if (bytes.length > 0) {
            req.contentType(MediaType.APPLICATION_JSON).content(bytes);
        }
        cred.assinador().cabecalhos(cred.keyId(), Instant.now().getEpochSecond(), metodo, caminho, bytes)
                .comoMapa().forEach(req::header);
        return mvc.perform(req);
    }

    ResultActions v1(String metodo, String caminho, String corpo) throws Exception {
        return chamar(grupo, metodo, caminho, corpo);
    }

    ResultActions admin(String metodo, String caminho, String corpo) throws Exception {
        return chamar(operador, metodo, caminho, corpo);
    }

    JsonNode corpo(ResultActions r) throws Exception {
        MvcResult res = r.andReturn();
        return json.readTree(res.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    // ---------- cenários comuns ----------

    void registrarConta(String contaId) throws Exception {
        v1("PUT", "/v1/contas/" + contaId, "{\"nomeExibicao\":\"Silva & Associados\",\"ativa\":true}");
    }

    void conectarWhatsapp(String contaId, String phoneNumberId) throws Exception {
        v1("POST", "/v1/contas/" + contaId + "/whatsapp/onboarding",
                "{\"code\":\"AQB123\",\"wabaId\":\"1104067312330972\",\"phoneNumberId\":\"" + phoneNumberId + "\"}");
    }

    /** Conta registrada, com WhatsApp conectado e template aprovado (Meta simulada). */
    void contaPronta(String contaId) throws Exception {
        registrarConta(contaId);
        conectarWhatsapp(contaId, String.valueOf(1_000_000_000L + Math.abs(contaId.hashCode() % 1_000_000)));
    }

    static String token(String prefixo) {
        byte[] b = new byte[16];
        RANDOM.nextBytes(b);
        return prefixo + Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    static String envio(String contaId, String referencia, String telefone, String botaoUrl) {
        return """
                {"contaId":"%s","referenciaExterna":"%s","destinatario":{"telefone":"%s"},
                 "template":{"nome":"comunicacao_registrada","idioma":"pt_BR",
                             "corpo":["Silva & Associados","K7M2-9QXH"],"botaoUrl":"%s"}}"""
                .formatted(contaId, referencia, telefone, botaoUrl);
    }

    static String envio(String contaId, String referencia, String telefone) {
        return envio(contaId, referencia, telefone, token(GRUPO));
    }

    String enfileirar(String contaId, String referencia, String telefone) throws Exception {
        return corpo(v1("POST", "/v1/envios", envio(contaId, referencia, telefone))).get("envioId").asString();
    }

    String statusDe(String envioId) {
        return jdbc.sql("SELECT status FROM envio_whatsapp WHERE envio_id = ?").param(envioId).query(String.class).single();
    }

    int usadoHoje(String contaId) {
        return jdbc.sql("SELECT COALESCE(SUM(usado), 0) FROM cota_uso WHERE conta_id = ? AND escopo = 'DIARIA'")
                .param(contaId).query(Integer.class).single();
    }

    static String env(String nome, String padrao) {
        String v = System.getenv(nome);
        return v == null ? padrao : v;
    }
}
