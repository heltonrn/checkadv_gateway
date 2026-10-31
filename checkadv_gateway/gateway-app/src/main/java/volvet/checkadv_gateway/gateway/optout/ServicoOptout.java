package volvet.checkadv_gateway.gateway.optout;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.callback.DadosOptoutRegistrado;
import volvet.checkadv_gateway.contrato.callback.TipoEvento;
import volvet.checkadv_gateway.contrato.optout.OrigemOptout;
import volvet.checkadv_gateway.contrato.optout.RemocaoOptout;
import volvet.checkadv_gateway.contrato.optout.SituacaoOptout;
import volvet.checkadv_gateway.gateway.callback.PublicadorEventos;
import volvet.checkadv_gateway.gateway.comum.Auditoria;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.cripto.ProtecaoDados;

/**
 * Opt-out por conta: o cliente pediu para não receber mensagens daquele escritório.
 * Guardado só como hash do telefone (+ máscara para exibição); nunca o número.
 */
@Service
public class ServicoOptout {

    private final JdbcClient jdbc;
    private final ProtecaoDados protecao;
    private final PublicadorEventos eventos;
    private final Auditoria auditoria;
    private final JsonMapper json;
    private final Clock relogio;

    public ServicoOptout(JdbcClient jdbc, ProtecaoDados protecao, PublicadorEventos eventos, Auditoria auditoria,
                         JsonMapper json, Clock relogio) {
        this.jdbc = jdbc;
        this.protecao = protecao;
        this.eventos = eventos;
        this.auditoria = auditoria;
        this.json = json;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public boolean bloqueado(String contaId, String telefoneHash) {
        return jdbc.sql("SELECT COUNT(*) FROM optout WHERE conta_id = ? AND telefone_hash = ? AND ativo = TRUE")
                .params(contaId, telefoneHash).query(Integer.class).single() > 0;
    }

    @Transactional(readOnly = true)
    public SituacaoOptout consultar(String contaId, String telefone) {
        Optional<Object[]> linha = jdbc.sql("""
                SELECT registrado_em, origem FROM optout
                 WHERE conta_id = ? AND telefone_hash = ? AND ativo = TRUE""")
                .params(contaId, protecao.hashTelefone(telefone))
                .query((rs, n) -> new Object[] {rs.getObject(1, LocalDateTime.class), rs.getString(2)})
                .optional();
        return linha.map(l -> new SituacaoOptout(true, Tempo.paraApi(Tempo.instante((LocalDateTime) l[0])),
                        OrigemOptout.valueOf((String) l[1])))
                .orElse(new SituacaoOptout(false, null, null));
    }

    /**
     * Registra (ou reativa) o opt-out e avisa a instância. Chamado pelo processamento de
     * mensagens recebidas (palavra-chave) — e pelo escritório no futuro.
     */
    @Transactional
    public void registrar(String grupo, String contaId, String telefone, OrigemOptout origem) {
        Instant agora = relogio.instant();
        String mascarado = ProtecaoDados.mascararTelefone(telefone);
        jdbc.sql("""
                INSERT INTO optout (conta_id, telefone_hash, telefone_mascarado, ativo, origem, registrado_em)
                VALUES (?, ?, ?, TRUE, ?, ?)
                ON DUPLICATE KEY UPDATE ativo = TRUE, origem = VALUES(origem), registrado_em = VALUES(registrado_em),
                                        removido_em = NULL, evidencia_remocao = NULL""")
                .params(contaId, protecao.hashTelefone(telefone), mascarado, origem.name(), Tempo.utc(agora))
                .update();
        eventos.publicar(grupo, contaId, TipoEvento.OPTOUT_REGISTRADO,
                new DadosOptoutRegistrado(mascarado, origem.name(), Tempo.paraApi(agora)), null, null);
    }

    /** @return false se não havia opt-out ativo */
    @Transactional
    public boolean remover(String ator, String contaId, RemocaoOptout remocao) {
        String hash = protecao.hashTelefone(remocao.telefone());
        int linhas = jdbc.sql("""
                UPDATE optout SET ativo = FALSE, removido_em = ?, evidencia_remocao = ?
                 WHERE conta_id = ? AND telefone_hash = ? AND ativo = TRUE""")
                .params(Tempo.utc(relogio.instant()), json.writeValueAsString(remocao.evidencia()), contaId, hash)
                .update();
        if (linhas > 0) {
            auditoria.registrar(ator, "OPTOUT_REMOVIDO", contaId,
                    ProtecaoDados.mascararTelefone(remocao.telefone()) + " " + remocao.evidencia().tipo()
                            + " por " + remocao.evidencia().registradoPor());
        }
        return linhas > 0;
    }
}
