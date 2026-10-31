package volvet.checkadv_gateway.gateway.meta;

import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import volvet.checkadv_gateway.contrato.conta.StatusTemplate;
import volvet.checkadv_gateway.contrato.envio.CategoriaCobranca;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;
import volvet.checkadv_gateway.gateway.config.MetaProperties;
import volvet.checkadv_gateway.gateway.webhook.ProcessadorStatusMeta;

/**
 * Meta simulada (Fase 1). Não faz nenhuma chamada externa.
 *
 * O final do telefone escolhe o comportamento, para testar cada caminho do worker:
 * <pre>
 *   ...0000  → falha definitiva: número sem WhatsApp (131026)
 *   ...0001  → falha temporária: limite de taxa (130429) — volta para a fila
 *   ...0002  → incerto (timeout depois de conectar)
 *   ...0003  → aceito, mas depois chega "failed" (131049) pelo webhook
 *   demais   → aceito; "delivered" e "read" chegam depois, como no webhook real
 * </pre>
 * Os status posteriores passam pelo mesmo {@link ProcessadorStatusMeta} do webhook.
 */
@Component
@ConditionalOnProperty(name = "app.meta.modo", havingValue = "simulado", matchIfMissing = true)
public class MetaClienteSimulado implements MetaCliente {

    private static final Logger log = LoggerFactory.getLogger(MetaClienteSimulado.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final MetaProperties.Simulado props;
    private final ProcessadorStatusMeta processador;
    private final ScheduledExecutorService agenda = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "meta-simulada");
        t.setDaemon(true);
        return t;
    });

    public MetaClienteSimulado(MetaProperties props, @Lazy ProcessadorStatusMeta processador) {
        this.props = props.simulado();
        this.processador = processador;
        log.warn("Meta SIMULADA ativa: nenhuma mensagem real será enviada.");
    }

    @Override
    public ResultadoEnvio enviarTemplate(EnvioMeta envio) {
        String fim = envio.telefone().substring(envio.telefone().length() - 4);
        switch (fim) {
            case "0000":
                return new ResultadoEnvio.FalhaDefinitiva(CategoriaErroEnvio.NUMERO_SEM_WHATSAPP, "131026",
                        "Message undeliverable (simulado)");
            case "0001":
                return new ResultadoEnvio.FalhaTemporaria("130429", "Rate limit hit (simulado)");
            case "0002":
                return new ResultadoEnvio.Incerto("Read timeout (simulado)");
            default:
                break;
        }
        String wamid = "wamid.SIM." + UUID.randomUUID();
        if (fim.equals("0003")) {
            agendar(props.atrasoEntrega(), () -> processador.aplicar(wamid, "failed", null, false,
                    "131049", "Not delivered to maintain healthy ecosystem engagement (simulado)"));
        } else {
            agendar(props.atrasoEntrega(), () -> processador.aplicar(wamid, "delivered",
                    CategoriaCobranca.UTILITY, true, null, null));
            agendar(props.atrasoLeitura(), () -> processador.aplicar(wamid, "read", null, null, null, null));
        }
        return new ResultadoEnvio.Aceito(wamid);
    }

    @Override
    public ResultadoOnboarding concluirOnboarding(String code, String wabaId, String phoneNumberId) {
        if (code.startsWith("FALHA")) {
            throw new MetaIndisponivelException("Code inválido ou expirado (simulado)");
        }
        String final4 = phoneNumberId.substring(Math.max(0, phoneNumberId.length() - 4));
        return new ResultadoOnboarding("SIMULADO-" + HexFormat.of().formatHex(bytes(16)),
                "+55 44 3333-" + final4, null, "GREEN", "TIER_250");
    }

    @Override
    public SituacaoTemplate garantirTemplate(String token, String wabaId, String nome, String idioma) {
        return props.aprovarTemplate()
                ? new SituacaoTemplate(StatusTemplate.APROVADO, null)
                : new SituacaoTemplate(StatusTemplate.PENDENTE, "Em análise (simulado)");
    }

    @Override
    public void removerInscricao(String token, String wabaId) {
        log.info("Inscrição removida da WABA {} (simulado)", wabaId);
    }

    private void agendar(Duration atraso, Runnable acao) {
        if (atraso == null || atraso.isZero() || atraso.isNegative()) {
            return;
        }
        agenda.schedule(() -> {
            try {
                acao.run();
            } catch (RuntimeException e) {
                log.warn("Falha ao aplicar status simulado", e);
            }
        }, atraso.toMillis(), TimeUnit.MILLISECONDS);
    }

    private static byte[] bytes(int n) {
        byte[] b = new byte[n];
        RANDOM.nextBytes(b);
        return b;
    }

    @PreDestroy
    void encerrar() {
        agenda.shutdownNow();
    }
}
