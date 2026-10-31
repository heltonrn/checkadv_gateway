package volvet.checkadv_gateway.gateway.fila;

import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.WorkerProperties;

/**
 * Fila no banco com {@code SELECT ... FOR UPDATE SKIP LOCKED} (MySQL 8.0+ / MariaDB 10.6+):
 * réplicas concorrentes pulam as linhas já travadas por outra, sem esperar nem duplicar.
 * A transação de reserva é curta; a chamada à Meta acontece depois, fora dela.
 */
@Component
public class FilaEnvioBanco implements FilaEnvio {

    private final JdbcClient jdbc;
    private final Clock relogio;
    private final WorkerProperties props;

    public FilaEnvioBanco(JdbcClient jdbc, Clock relogio, WorkerProperties props) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.props = props;
    }

    @Override
    @Transactional
    public List<String> reservar(int lote) {
        Instant agora = relogio.instant();
        List<String> ids = jdbc.sql("""
                SELECT envio_id FROM envio_whatsapp
                 WHERE status = 'ENFILEIRADO' AND proxima_tentativa <= ?
                 ORDER BY proxima_tentativa
                 LIMIT ?
                 FOR UPDATE SKIP LOCKED""")
                .params(Tempo.utc(agora), lote)
                .query(String.class).list();
        if (ids.isEmpty()) {
            return Collections.emptyList();
        }
        String in = String.join(",", Collections.nCopies(ids.size(), "?"));
        jdbc.sql("UPDATE envio_whatsapp SET status = 'ENVIANDO', lease_ate = ?, tentativas = tentativas + 1, atualizado_em = ?"
                        + " WHERE envio_id IN (" + in + ")")
                .params(concat(Tempo.utc(agora.plus(props.lease())), Tempo.utc(agora), ids))
                .update();
        return ids;
    }

    private static List<Object> concat(Object a, Object b, List<String> ids) {
        var lista = new java.util.ArrayList<Object>(ids.size() + 2);
        lista.add(a);
        lista.add(b);
        lista.addAll(ids);
        return lista;
    }
}
