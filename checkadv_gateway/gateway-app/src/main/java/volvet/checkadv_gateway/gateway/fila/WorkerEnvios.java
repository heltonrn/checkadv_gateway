package volvet.checkadv_gateway.gateway.fila;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import jakarta.annotation.PreDestroy;
import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.WorkerProperties;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfig;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfigRepository;
import volvet.checkadv_gateway.gateway.cripto.ProtecaoDados;
import volvet.checkadv_gateway.gateway.envio.EnvioWhatsapp;
import volvet.checkadv_gateway.gateway.envio.EnvioWhatsappRepository;
import volvet.checkadv_gateway.gateway.envio.ParametrosTemplate;
import volvet.checkadv_gateway.gateway.envio.TransicoesEnvio;
import volvet.checkadv_gateway.gateway.envio.TransicoesEnvio.Alteracao;
import volvet.checkadv_gateway.gateway.meta.MetaCliente;
import volvet.checkadv_gateway.gateway.meta.MetaCliente.ResultadoEnvio;

/**
 * Worker de envio. Cada ciclo:
 * <ol>
 *   <li>reserva um lote (transação curta, SKIP LOCKED) → ENVIANDO com lease;</li>
 *   <li>chama a Meta FORA de transação, em pool limitado e respeitando o limite por número;</li>
 *   <li>registra o resultado com transição condicional (só se ainda estiver ENVIANDO).</li>
 * </ol>
 * Se a réplica morrer no meio, o varredor encontra o lease vencido e marca INCERTO —
 * nunca volta sozinho para a fila, porque a Meta pode ter recebido.
 */
@Component
public class WorkerEnvios {

    private static final Logger log = LoggerFactory.getLogger(WorkerEnvios.class);

    /** Espera entre tentativas após falha temporária (30 s, 2 min, 10 min, 30 min, 2 h). */
    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(30), Duration.ofMinutes(2),
            Duration.ofMinutes(10), Duration.ofMinutes(30), Duration.ofHours(2));

    private final FilaEnvio fila;
    private final EnvioWhatsappRepository envios;
    private final WhatsappContaConfigRepository configs;
    private final TransicoesEnvio transicoes;
    private final MetaCliente meta;
    private final ProtecaoDados protecao;
    private final LimitadorTaxa limitador;
    private final JsonMapper json;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final WorkerProperties props;
    private final Clock relogio;
    private final ExecutorService pool;

    public WorkerEnvios(FilaEnvio fila, EnvioWhatsappRepository envios, WhatsappContaConfigRepository configs,
                        TransicoesEnvio transicoes, MetaCliente meta, ProtecaoDados protecao, LimitadorTaxa limitador,
                        JsonMapper json, JdbcClient jdbc, TransactionTemplate tx, WorkerProperties props, Clock relogio) {
        this.fila = fila;
        this.envios = envios;
        this.configs = configs;
        this.transicoes = transicoes;
        this.meta = meta;
        this.protecao = protecao;
        this.limitador = limitador;
        this.json = json;
        this.jdbc = jdbc;
        this.tx = tx;
        this.props = props;
        this.relogio = relogio;
        this.pool = Executors.newFixedThreadPool(Math.max(1, props.threads()), r -> {
            Thread t = new Thread(r, "worker-envio");
            t.setDaemon(true);
            return t;
        });
    }

    /** Um ciclo completo; devolve quantos envios foram processados. */
    public int ciclo() {
        List<String> ids = fila.reservar(props.lote());
        if (ids.isEmpty()) {
            return 0;
        }
        List<Callable<Void>> tarefas = new ArrayList<>();
        ids.forEach(id -> tarefas.add(() -> {
            processar(id);
            return null;
        }));
        try {
            pool.invokeAll(tarefas);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        return ids.size();
    }

    private record Preparado(MetaCliente.EnvioMeta envio, int tentativas) {
    }

    void processar(String envioId) {
        try {
            Preparado p = tx.execute(s -> preparar(envioId));
            if (p == null) {
                return; // já registrado como FALHOU na preparação
            }
            limitador.aguardar(p.envio().phoneNumberId());
            ResultadoEnvio resultado;
            try {
                resultado = meta.enviarTemplate(p.envio());
            } catch (RuntimeException e) {
                log.error("Erro inesperado chamando a Meta para o envio {}", envioId, e);
                resultado = new ResultadoEnvio.Incerto("Erro inesperado: " + e.getClass().getSimpleName());
            }
            ResultadoEnvio r = resultado;
            tx.executeWithoutResult(s -> registrar(envioId, p.tentativas(), r));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException e) {
            // O lease vence e o varredor marca INCERTO; nada é reenviado às cegas.
            log.error("Falha processando envio {}", envioId, e);
        }
    }

    private Preparado preparar(String envioId) {
        EnvioWhatsapp e = envios.findById(envioId).orElseThrow();
        WhatsappContaConfig config = configs.findById(e.getContaId()).orElse(null);
        if (config == null || config.getStatus() != StatusWhatsapp.CONECTADA || config.getTokenCifrado() == null) {
            falhar(envioId, CategoriaErroEnvio.CONTA_DESCONECTADA, "WhatsApp da conta não está conectado.");
            return null;
        }
        if (e.getParametrosJson() == null) {
            falhar(envioId, CategoriaErroEnvio.ERRO_INTERNO, "Parâmetros do envio já foram apagados.");
            return null;
        }
        String chave = protecao.chaveCifraId();
        String token = protecao.decifrar(config.getTokenCifrado(), config.getChaveCifraId(),
                WhatsappContaConfig.contextoToken(e.getContaId()));
        String telefone = protecao.decifrar(e.getTelefoneCifrado(), chave, EnvioWhatsapp.contextoTelefone(envioId));
        ParametrosTemplate parametros = json.readValue(
                protecao.decifrar(e.getParametrosJson(), chave, EnvioWhatsapp.contextoParametros(envioId)),
                ParametrosTemplate.class);
        return new Preparado(new MetaCliente.EnvioMeta(envioId, config.getPhoneNumberId(), token, telefone,
                e.getTemplateNome(), e.getTemplateIdioma(), parametros.corpo(), parametros.botaoUrl()), e.getTentativas());
    }

    private void registrar(String envioId, int tentativas, ResultadoEnvio resultado) {
        List<StatusEnvio> deEnviando = List.of(StatusEnvio.ENVIANDO);
        switch (resultado) {
            case ResultadoEnvio.Aceito a ->
                    transicoes.aplicar(envioId, deEnviando, StatusEnvio.ENVIADO, Alteracao.comWamid(a.wamid()));
            case ResultadoEnvio.FalhaDefinitiva f ->
                    transicoes.aplicar(envioId, deEnviando, StatusEnvio.FALHOU,
                            Alteracao.comErro(f.categoria(), f.mensagem(), f.codigoMeta()));
            case ResultadoEnvio.FalhaTemporaria t -> reagendarOuFalhar(envioId, tentativas, t.codigoMeta(), t.motivo());
            case ResultadoEnvio.Incerto i -> {
                if (props.retentarApos5xx()) {
                    reagendarOuFalhar(envioId, tentativas, null, i.motivo());
                } else {
                    transicoes.aplicar(envioId, deEnviando, StatusEnvio.INCERTO,
                            Alteracao.comErro(CategoriaErroEnvio.ENVIO_INCERTO, i.motivo(), null));
                }
            }
        }
    }

    private void reagendarOuFalhar(String envioId, int tentativas, String codigoMeta, String motivo) {
        if (tentativas >= props.maxTentativas()) {
            CategoriaErroEnvio categoria = "130429".equals(codigoMeta)
                    ? CategoriaErroEnvio.LIMITE_META_EXCEDIDO : CategoriaErroEnvio.ERRO_META;
            transicoes.aplicar(envioId, List.of(StatusEnvio.ENVIANDO), StatusEnvio.FALHOU,
                    Alteracao.comErro(categoria, "Tentativas esgotadas: " + motivo, codigoMeta));
            return;
        }
        Duration espera = BACKOFF.get(Math.min(tentativas, BACKOFF.size()) - 1);
        Instant quando = relogio.instant().plus(espera);
        transicoes.aplicar(envioId, List.of(StatusEnvio.ENVIANDO), StatusEnvio.ENFILEIRADO,
                Alteracao.reagendar(quando, motivo));
        log.info("Envio {} reagendado em {} (tentativa {}): {}", envioId, espera, tentativas, motivo);
    }

    private void falhar(String envioId, CategoriaErroEnvio categoria, String mensagem) {
        transicoes.aplicar(envioId, List.of(StatusEnvio.ENVIANDO), StatusEnvio.FALHOU,
                Alteracao.comErro(categoria, mensagem, null));
    }

    /** ENVIANDO com lease vencido → INCERTO (a réplica pode ter morrido depois de chamar a Meta). */
    public int varrerLeasesVencidos() {
        Integer total = tx.execute(s -> {
            List<String> ids = jdbc.sql("""
                    SELECT envio_id FROM envio_whatsapp
                     WHERE status = 'ENVIANDO' AND lease_ate < ?
                     LIMIT 100 FOR UPDATE SKIP LOCKED""")
                    .param(Tempo.utc(relogio.instant())).query(String.class).list();
            ids.forEach(id -> transicoes.aplicar(id, List.of(StatusEnvio.ENVIANDO), StatusEnvio.INCERTO,
                    Alteracao.comErro(CategoriaErroEnvio.ENVIO_INCERTO, "Processamento interrompido (lease vencido).", null)));
            return ids.size();
        });
        if (total != null && total > 0) {
            log.warn("{} envio(s) marcados INCERTO por lease vencido", total);
        }
        return total == null ? 0 : total;
    }

    /**
     * Apaga os parâmetros (que contêm o token do link) de envios que já saíram há mais
     * que a retenção. O token só é necessário para enviar.
     */
    public int purgarParametros() {
        Instant limite = relogio.instant().minus(props.retencao());
        return jdbc.sql("""
                UPDATE envio_whatsapp SET parametros_json = NULL, purgado_em = ?
                 WHERE purgado_em IS NULL AND atualizado_em < ?
                   AND status IN ('ENVIADO', 'ENTREGUE', 'LIDO', 'FALHOU', 'INCERTO', 'CANCELADO')""")
                .params(Tempo.utc(relogio.instant()), Tempo.utc(limite)).update();
    }

    @PreDestroy
    void encerrar() {
        pool.shutdownNow();
    }
}
