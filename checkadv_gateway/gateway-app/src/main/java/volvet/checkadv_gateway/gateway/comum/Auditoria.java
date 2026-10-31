package volvet.checkadv_gateway.gateway.comum;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** Trilha de quem fez o quê (operações administrativas e de dados sensíveis). Nunca grava segredos. */
@Component
public class Auditoria {

    private static final Logger log = LoggerFactory.getLogger(Auditoria.class);

    private final JdbcClient jdbc;
    private final Clock relogio;

    public Auditoria(JdbcClient jdbc, Clock relogio) {
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    public void registrar(String ator, String acao, String alvo, String detalhe) {
        log.info("Auditoria: {} {} {} {}", ator, acao, alvo, detalhe == null ? "" : detalhe);
        jdbc.sql("INSERT INTO auditoria (ocorrido_em, ator, acao, alvo, detalhe) VALUES (?, ?, ?, ?, ?)")
                .params(Tempo.utc(relogio.instant()), ator, acao, alvo,
                        detalhe == null ? null : detalhe.substring(0, Math.min(detalhe.length(), 500)))
                .update();
    }
}
