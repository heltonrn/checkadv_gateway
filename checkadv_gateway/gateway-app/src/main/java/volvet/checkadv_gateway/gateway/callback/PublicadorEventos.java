package volvet.checkadv_gateway.gateway.callback;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.callback.EventoCallback;
import volvet.checkadv_gateway.contrato.callback.TipoEvento;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.CallbackProperties;

/**
 * Grava eventos para a instância na outbox {@code callback_pendente}.
 *
 * Decisão: o evento é gravado na MESMA transação da mudança que ele descreve
 * (Propagation.MANDATORY). Ou os dois acontecem, ou nenhum: não existe status alterado
 * sem callback, nem callback de algo que foi desfeito. Quem entrega é o
 * {@link DespachanteCallbacks}, fora da transação.
 */
@Component
public class PublicadorEventos {

    private final JdbcClient jdbc;
    private final JsonMapper json;
    private final Clock relogio;
    private final CallbackProperties props;

    public PublicadorEventos(JdbcClient jdbc, JsonMapper json, Clock relogio, CallbackProperties props) {
        this.jdbc = jdbc;
        this.json = json;
        this.relogio = relogio;
        this.props = props;
    }

    /**
     * @param envioId   e {@code sequencia} só para {@code envio.status_alterado}
     *                  (garantem um evento por transição)
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public UUID publicar(String grupoPrefixo, String contaId, TipoEvento tipo, Object dados,
                         String envioId, Integer sequencia) {
        UUID eventoId = UUID.randomUUID();
        Instant agora = relogio.instant();
        var evento = new EventoCallback<>(eventoId, tipo, contaId, Tempo.paraApi(agora), dados);
        jdbc.sql("""
                INSERT INTO callback_pendente
                  (evento_id, grupo_prefixo, conta_id, tipo, envio_id, sequencia, payload_json,
                   status, tentativas, proxima_tentativa, prazo_ate, criado_em)
                VALUES (?, ?, ?, ?, ?, ?, ?, 'PENDENTE', 0, ?, ?, ?)""")
                .params(eventoId.toString(), grupoPrefixo, contaId, tipo.codigo(), envioId, sequencia,
                        json.writeValueAsString(evento), Tempo.utc(agora), Tempo.utc(agora.plus(props.prazo())),
                        Tempo.utc(agora))
                .update();
        return eventoId;
    }
}
