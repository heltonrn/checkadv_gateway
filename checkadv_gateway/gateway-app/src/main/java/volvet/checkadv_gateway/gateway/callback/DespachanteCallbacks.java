package volvet.checkadv_gateway.gateway.callback;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.CallbackProperties;
import volvet.checkadv_gateway.gateway.seguranca.ChaveResolvida;
import volvet.checkadv_gateway.gateway.seguranca.ChavesGrupo;

/**
 * Entrega os eventos da outbox à instância do grupo: POST na URL registrada, assinado com
 * a chave do grupo (mesmo esquema HMAC da API v1).
 *
 * Reserva com SKIP LOCKED e "lease" (empurra proxima_tentativa) numa transação curta;
 * envia fora dela; registra o resultado. 2xx = ENTREGUE. Senão, retenta em
 * 10 s, 1 min, 5 min, 30 min, 2 h, 6 h... até o prazo (24 h) → CALLBACK_FALHOU, e a
 * instância reconcilia pelo GET do envio.
 */
@Component
public class DespachanteCallbacks {

    private static final Logger log = LoggerFactory.getLogger(DespachanteCallbacks.class);

    static final List<Duration> BACKOFF = List.of(Duration.ofSeconds(10), Duration.ofMinutes(1), Duration.ofMinutes(5),
            Duration.ofMinutes(30), Duration.ofHours(2), Duration.ofHours(6));
    private static final Duration LEASE = Duration.ofSeconds(60);

    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final ChavesGrupo chaves;
    private final CallbackProperties props;
    private final Clock relogio;
    private final HttpClient http;

    public DespachanteCallbacks(JdbcClient jdbc, TransactionTemplate tx, ChavesGrupo chaves,
                                CallbackProperties props, Clock relogio) {
        this.jdbc = jdbc;
        this.tx = tx;
        this.chaves = chaves;
        this.props = props;
        this.relogio = relogio;
        this.http = HttpClient.newBuilder()
                .connectTimeout(props.timeoutConexao())
                .followRedirects(HttpClient.Redirect.NEVER)
                .build();
    }

    private record Pendente(long id, String eventoId, String grupo, String payload, int tentativas,
                            Instant prazo, String url) {
    }

    /** @return quantos callbacks foram tentados neste ciclo */
    public int ciclo() {
        List<Pendente> lote = tx.execute(s -> reservar());
        if (lote == null || lote.isEmpty()) {
            return 0;
        }
        lote.forEach(this::entregar);
        return lote.size();
    }

    private List<Pendente> reservar() {
        Instant agora = relogio.instant();
        List<Pendente> pendentes = jdbc.sql("""
                SELECT c.id, c.evento_id, c.grupo_prefixo, c.payload_json, c.tentativas,
                       COALESCE(c.prazo_ate, c.criado_em), g.callback_url
                  FROM callback_pendente c JOIN grupo_instancia g ON g.prefixo = c.grupo_prefixo
                 WHERE c.status = 'PENDENTE' AND c.proxima_tentativa <= ?
                 ORDER BY c.id
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED""")
                .params(Tempo.utc(agora), props.lote())
                .query((rs, n) -> new Pendente(rs.getLong(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getInt(5), Tempo.instante(rs.getObject(6, LocalDateTime.class)), rs.getString(7)))
                .list();
        if (!pendentes.isEmpty()) {
            String in = String.join(",", Collections.nCopies(pendentes.size(), "?"));
            var params = new java.util.ArrayList<Object>();
            params.add(Tempo.utc(agora.plus(LEASE)));
            pendentes.forEach(p -> params.add(p.id()));
            jdbc.sql("UPDATE callback_pendente SET proxima_tentativa = ? WHERE id IN (" + in + ")").params(params).update();
        }
        return pendentes;
    }

    private void entregar(Pendente p) {
        String erro;
        if (p.url() == null) {
            erro = "Grupo sem URL de callback registrada";
        } else {
            erro = enviar(p);
        }
        Instant agora = relogio.instant();
        if (erro == null) {
            jdbc.sql("UPDATE callback_pendente SET status = 'ENTREGUE', entregue_em = ?, tentativas = tentativas + 1, ultimo_erro = NULL WHERE id = ?")
                    .params(Tempo.utc(agora), p.id()).update();
            return;
        }
        int tentativas = p.tentativas() + 1;
        Instant proxima = agora.plus(BACKOFF.get(Math.min(tentativas, BACKOFF.size()) - 1));
        boolean desistir = proxima.isAfter(p.prazo());
        jdbc.sql("UPDATE callback_pendente SET status = ?, tentativas = ?, proxima_tentativa = ?, ultimo_erro = ? WHERE id = ?")
                .params(desistir ? "CALLBACK_FALHOU" : "PENDENTE", tentativas, Tempo.utc(proxima),
                        erro.length() > 255 ? erro.substring(0, 255) : erro, p.id())
                .update();
        if (desistir) {
            log.error("Callback {} do grupo {} desistido após {} tentativas: {}", p.eventoId(), p.grupo(), tentativas, erro);
        } else {
            log.warn("Callback {} do grupo {} falhou (tentativa {}): {}", p.eventoId(), p.grupo(), tentativas, erro);
        }
    }

    /** @return null em caso de sucesso; senão, o motivo da falha */
    private String enviar(Pendente p) {
        Optional<ChaveResolvida> chave = chaves.paraAssinarCallback(p.grupo());
        if (chave.isEmpty()) {
            return "Grupo sem chave ativa para assinar";
        }
        try {
            URI uri = URI.create(p.url());
            String caminho = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
            byte[] corpo = p.payload().getBytes(StandardCharsets.UTF_8);
            var cab = chave.get().assinador().cabecalhos(chave.get().keyId(), relogio.instant().getEpochSecond(),
                    "POST", caminho.isEmpty() ? "/" : caminho, corpo);
            HttpRequest req = HttpRequest.newBuilder(uri)
                    .timeout(props.timeoutLeitura())
                    .header("Content-Type", "application/json")
                    .header(CabecalhosGateway.KEY_ID, cab.keyId())
                    .header(CabecalhosGateway.TIMESTAMP, Long.toString(cab.timestamp()))
                    .header(CabecalhosGateway.ASSINATURA, cab.assinatura())
                    .POST(HttpRequest.BodyPublishers.ofByteArray(corpo))
                    .build();
            HttpResponse<Void> resp = http.send(req, HttpResponse.BodyHandlers.discarding());
            return resp.statusCode() / 100 == 2 ? null : "HTTP " + resp.statusCode();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return "Interrompido";
        } catch (Exception e) {
            return e.getClass().getSimpleName() + (e.getMessage() == null ? "" : ": " + e.getMessage());
        }
    }
}
