package volvet.checkadv_gateway.gateway.envio;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.callback.DadosEnvioStatus;
import volvet.checkadv_gateway.contrato.callback.TipoEvento;
import volvet.checkadv_gateway.contrato.envio.CategoriaCobranca;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;
import volvet.checkadv_gateway.contrato.envio.Cobranca;
import volvet.checkadv_gateway.contrato.envio.ErroEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.gateway.callback.PublicadorEventos;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.cota.ServicoCota;

/**
 * ÚNICO ponto que muda o status de um envio.
 *
 * Cada transição é um UPDATE condicional ({@code WHERE status IN (origens)}): se outro
 * processo (outra réplica, o webhook, o varredor) chegou antes, a linha não é afetada e
 * nada mais acontece. Quando afeta, na mesma transação:
 * <ul>
 *   <li>transição notificável: incrementa {@code sequencia}, grava histórico e callback;</li>
 *   <li>FALHOU ou CANCELADO: devolve a cota (INCERTO continua contando).</li>
 * </ul>
 */
@Component
public class TransicoesEnvio {

    private static final Logger log = LoggerFactory.getLogger(TransicoesEnvio.class);

    private final JdbcClient jdbc;
    private final Clock relogio;
    private final PublicadorEventos eventos;
    private final ServicoCota cota;

    public TransicoesEnvio(JdbcClient jdbc, Clock relogio, PublicadorEventos eventos, ServicoCota cota) {
        this.jdbc = jdbc;
        this.relogio = relogio;
        this.eventos = eventos;
        this.cota = cota;
    }

    /** Dados opcionais gravados junto com a transição. */
    public record Alteracao(String wamid, ErroEnvio erro, CategoriaCobranca categoriaCobranca, Boolean cobravel,
                            Instant proximaTentativa, String detalhe) {

        public static Alteracao nenhuma() {
            return new Alteracao(null, null, null, null, null, null);
        }

        public static Alteracao comWamid(String wamid) {
            return new Alteracao(wamid, null, CategoriaCobranca.UTILITY, null, null, null);
        }

        public static Alteracao comErro(CategoriaErroEnvio categoria, String mensagem, String codigoMeta) {
            return new Alteracao(null, new ErroEnvio(categoria, mensagem, codigoMeta), null, null, null, null);
        }

        public static Alteracao reagendar(Instant quando, String motivo) {
            return new Alteracao(null, null, null, null, quando, motivo);
        }

        public static Alteracao cobranca(CategoriaCobranca categoria, Boolean cobravel) {
            return new Alteracao(null, null, categoria, cobravel, null, null);
        }
    }

    /** @return true se a transição foi aplicada por esta chamada */
    @Transactional(propagation = Propagation.MANDATORY)
    public boolean aplicar(String envioId, Collection<StatusEnvio> origens, StatusEnvio destino, Alteracao a) {
        Instant agora = relogio.instant();
        List<String> sets = new ArrayList<>(List.of("status = ?", "atualizado_em = ?", "lease_ate = NULL"));
        List<Object> params = new ArrayList<>(List.of(destino.name(), Tempo.utc(agora)));
        if (destino.notificavel()) {
            sets.add("sequencia = sequencia + 1");
        }
        if (a.wamid() != null) {
            sets.add("wamid = ?");
            params.add(a.wamid());
        }
        if (a.erro() != null) {
            sets.add("erro_categoria = ?");
            sets.add("erro_mensagem = ?");
            sets.add("erro_codigo_meta = ?");
            params.add(a.erro().categoria().name());
            params.add(truncar(a.erro().mensagem(), 255));
            params.add(a.erro().codigoMeta());
        }
        if (a.categoriaCobranca() != null) {
            sets.add("categoria_cobranca = ?");
            params.add(a.categoriaCobranca().name());
        }
        if (a.cobravel() != null) {
            sets.add("cobravel = ?");
            params.add(a.cobravel());
        }
        if (a.proximaTentativa() != null) {
            sets.add("proxima_tentativa = ?");
            params.add(Tempo.utc(a.proximaTentativa()));
        }
        String in = String.join(",", origens.stream().map(s -> "?").toList());
        params.add(envioId);
        origens.forEach(o -> params.add(o.name()));

        int linhas = jdbc.sql("UPDATE envio_whatsapp SET " + String.join(", ", sets)
                        + " WHERE envio_id = ? AND status IN (" + in + ")")
                .params(params).update();
        if (linhas == 0) {
            log.debug("Transição {} → {} ignorada para {}: status atual não permite", origens, destino, envioId);
            return false;
        }

        Estado e = estado(envioId);
        if (destino.notificavel()) {
            jdbc.sql("INSERT INTO envio_historico (envio_id, status, sequencia, ocorrido_em, detalhe) VALUES (?, ?, ?, ?, ?)")
                    .params(envioId, destino.name(), e.sequencia(), Tempo.utc(agora), truncar(a.detalhe(), 255))
                    .update();
            ErroEnvio erro = e.erroCategoria() == null ? null
                    : new ErroEnvio(CategoriaErroEnvio.valueOf(e.erroCategoria()), e.erroMensagem(), e.erroCodigoMeta());
            Cobranca cobranca = e.categoriaCobranca() == null && e.cobravel() == null ? null
                    : new Cobranca(e.categoriaCobranca() == null ? null : CategoriaCobranca.valueOf(e.categoriaCobranca()),
                    e.cobravel());
            eventos.publicar(e.grupoPrefixo(), e.contaId(), TipoEvento.ENVIO_STATUS_ALTERADO,
                    new DadosEnvioStatus(UUID.fromString(envioId), e.referenciaExterna(), destino, e.sequencia(),
                            erro, cobranca),
                    envioId, e.sequencia());
        }
        if (destino == StatusEnvio.FALHOU || destino == StatusEnvio.CANCELADO) {
            cota.liberar(e.contaId(), e.cotaDia(), e.cotaPeriodoInicio(), e.excedente());
        }
        return true;
    }

    /** Só atualiza dados de cobrança, sem mudar status (status de pagamento chega depois). */
    @Transactional(propagation = Propagation.MANDATORY)
    public void atualizarCobranca(String envioId, CategoriaCobranca categoria, Boolean cobravel) {
        jdbc.sql("UPDATE envio_whatsapp SET categoria_cobranca = COALESCE(?, categoria_cobranca), cobravel = COALESCE(?, cobravel) WHERE envio_id = ?")
                .params(categoria == null ? null : categoria.name(), cobravel, envioId).update();
    }

    private record Estado(String contaId, String grupoPrefixo, String referenciaExterna, int sequencia,
                          String erroCategoria, String erroMensagem, String erroCodigoMeta,
                          String categoriaCobranca, Boolean cobravel,
                          LocalDate cotaDia, LocalDate cotaPeriodoInicio, boolean excedente) {
    }

    private Estado estado(String envioId) {
        return jdbc.sql("""
                SELECT e.conta_id, c.grupo_prefixo, e.referencia_externa, e.sequencia,
                       e.erro_categoria, e.erro_mensagem, e.erro_codigo_meta, e.categoria_cobranca, e.cobravel,
                       e.cota_dia, e.cota_periodo_inicio, e.excedente
                  FROM envio_whatsapp e JOIN conta c ON c.conta_id = e.conta_id
                 WHERE e.envio_id = ?""")
                .param(envioId)
                .query((rs, n) -> new Estado(rs.getString(1), rs.getString(2), rs.getString(3), rs.getInt(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8),
                        (Boolean) rs.getObject(9, Boolean.class),
                        rs.getObject(10, LocalDate.class), rs.getObject(11, LocalDate.class), rs.getBoolean(12)))
                .single();
    }

    private static String truncar(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
