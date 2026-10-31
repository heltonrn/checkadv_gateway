package volvet.checkadv_gateway.gateway.cota;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.conta.DefinicaoCota;
import volvet.checkadv_gateway.contrato.conta.EscopoCota;
import volvet.checkadv_gateway.contrato.conta.PoliticaExcesso;
import volvet.checkadv_gateway.contrato.conta.UsoCota;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.CotaProperties;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;

/**
 * Cotas por conta, com contadores atômicos em {@code cota_uso}.
 *
 * Decisão: nada de "ler e depois gravar". A reserva é um único
 * {@code UPDATE ... SET usado = usado + 1 WHERE ... AND usado < limite}; se nenhuma linha
 * muda, a cota acabou. Roda na MESMA transação do insert do envio: qualquer erro depois
 * desfaz a reserva junto.
 *
 * Duas janelas: DIARIA (sempre; protege o limite da Meta) e MENSAL (o período do plano,
 * só quando a conta tem cota definida).
 */
@Service
public class ServicoCota {

    private final JdbcClient jdbc;
    private final CotaConfigRepository configs;
    private final CotaProperties props;
    private final Clock relogio;

    public ServicoCota(JdbcClient jdbc, CotaConfigRepository configs, CotaProperties props, Clock relogio) {
        this.jdbc = jdbc;
        this.configs = configs;
        this.props = props;
        this.relogio = relogio;
    }

    /** Janelas consumidas por um envio; {@code periodoInicio} nulo = sem limite de período. */
    public record Reserva(LocalDate dia, LocalDate periodoInicio, boolean excedente, int restante) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Reserva reservar(String contaId) {
        LocalDate hoje = hoje();
        Optional<CotaConfig> config = configs.findById(contaId);
        int limiteDia = config.map(CotaConfig::getLimiteDiario).orElse(props.limiteDiarioPadrao());

        garantirJanela(contaId, EscopoCota.DIARIA, hoje, hoje, limiteDia);
        if (incrementar(contaId, EscopoCota.DIARIA, hoje) == 0) {
            throw cotaExcedida(EscopoCota.DIARIA, "Cota diária da conta atingida.", contaId, hoje);
        }

        LocalDate periodoInicio = null;
        boolean excedente = false;
        if (config.isPresent()) {
            CotaConfig c = config.get();
            if (!c.vigenteEm(hoje)) {
                throw new ExcecaoGateway(HttpStatus.TOO_MANY_REQUESTS, CodigoErro.COTA_EXCEDIDA,
                        "Período da cota fora da vigência; o plano precisa ser renovado.",
                        Map.of("tipo", EscopoCota.MENSAL, "periodoInicio", c.getPeriodoInicio().toString(),
                                "periodoFim", c.getPeriodoFim().toString()));
            }
            periodoInicio = c.getPeriodoInicio();
            garantirJanela(contaId, EscopoCota.MENSAL, periodoInicio, c.getPeriodoFim(), c.getLimite());
            if (incrementar(contaId, EscopoCota.MENSAL, periodoInicio) == 0) {
                if (c.getPoliticaExcesso() != PoliticaExcesso.PERMITIR_COBRAR_EXTRA) {
                    throw cotaExcedida(EscopoCota.MENSAL, "Cota do período atingida.", contaId, periodoInicio);
                }
                jdbc.sql("""
                        UPDATE cota_uso SET usado = usado + 1, excedente = excedente + 1
                         WHERE conta_id = ? AND escopo = 'MENSAL' AND janela_inicio = ?""")
                        .params(contaId, periodoInicio).update();
                excedente = true;
            }
        }
        return new Reserva(hoje, periodoInicio, excedente, restante(contaId, hoje, periodoInicio));
    }

    /**
     * Devolve a cota de um envio que não saiu (CANCELADO ou FALHOU). Chamado só quando a
     * transição de status afetou a linha, então nunca devolve duas vezes.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void liberar(String contaId, LocalDate dia, LocalDate periodoInicio, boolean excedente) {
        if (dia != null) {
            jdbc.sql("""
                    UPDATE cota_uso SET usado = GREATEST(usado - 1, 0)
                     WHERE conta_id = ? AND escopo = 'DIARIA' AND janela_inicio = ?""")
                    .params(contaId, dia).update();
        }
        if (periodoInicio != null) {
            jdbc.sql("""
                    UPDATE cota_uso SET usado = GREATEST(usado - 1, 0),
                           excedente = CASE WHEN ? THEN GREATEST(excedente - 1, 0) ELSE excedente END
                     WHERE conta_id = ? AND escopo = 'MENSAL' AND janela_inicio = ?""")
                    .params(excedente, contaId, periodoInicio).update();
        }
    }

    @Transactional
    public UsoCota definir(String contaId, DefinicaoCota definicao) {
        if (definicao.periodoFim().isBefore(definicao.periodoInicio())) {
            throw new ExcecaoGateway(HttpStatus.BAD_REQUEST, CodigoErro.PAYLOAD_INVALIDO,
                    "periodoFim anterior a periodoInicio.", Map.of("campos", Map.of("periodoFim", "antes do início")));
        }
        Instant agora = relogio.instant();
        CotaConfig config = configs.findById(contaId).orElseGet(() -> CotaConfig.nova(contaId));
        config.definir(definicao.periodoInicio(), definicao.periodoFim(), definicao.limite(),
                definicao.limiteDiario(), definicao.politicaExcesso(), agora);
        configs.saveAndFlush(config);

        // Janelas já abertas passam a valer o limite novo na hora.
        jdbc.sql("UPDATE cota_uso SET limite = ?, janela_fim = ? WHERE conta_id = ? AND escopo = 'MENSAL' AND janela_inicio = ?")
                .params(definicao.limite(), definicao.periodoFim(), contaId, definicao.periodoInicio()).update();
        jdbc.sql("UPDATE cota_uso SET limite = ? WHERE conta_id = ? AND escopo = 'DIARIA' AND janela_inicio >= ?")
                .params(definicao.limiteDiario(), contaId, hoje()).update();
        return uso(contaId);
    }

    @Transactional(readOnly = true)
    public UsoCota uso(String contaId) {
        LocalDate hoje = hoje();
        Optional<CotaConfig> config = configs.findById(contaId);
        int limiteDia = config.map(CotaConfig::getLimiteDiario).orElse(props.limiteDiarioPadrao());
        int usadoDia = usado(contaId, EscopoCota.DIARIA, hoje).map(u -> u.usado).orElse(0);

        UsoCota.Periodo periodo = null;
        Integer limite = null;
        Integer usado = null;
        Integer excedente = null;
        if (config.isPresent()) {
            CotaConfig c = config.get();
            periodo = new UsoCota.Periodo(c.getPeriodoInicio(), c.getPeriodoFim());
            limite = c.getLimite();
            Optional<Contador> mensal = usado(contaId, EscopoCota.MENSAL, c.getPeriodoInicio());
            usado = mensal.map(u -> u.usado).orElse(0);
            excedente = mensal.map(u -> u.excedente).orElse(0);
        }
        Instant desde = relogio.instant().minus(24, ChronoUnit.HOURS);
        Integer unicos = jdbc.sql("""
                SELECT COUNT(DISTINCT telefone_hash) FROM envio_whatsapp
                 WHERE conta_id = ? AND criado_em >= ? AND status <> 'CANCELADO'""")
                .params(contaId, Tempo.utc(desde)).query(Integer.class).single();
        Integer restante = limite == null ? null : Math.max(limite - usado, 0);
        return new UsoCota(periodo, limite, usado, restante, excedente,
                new UsoCota.Diario(limiteDia, usadoDia, Math.max(limiteDia - usadoDia, 0)), unicos);
    }

    /** Menor saldo entre dia e período, para o header X-Cota-Restante. */
    private int restante(String contaId, LocalDate dia, LocalDate periodoInicio) {
        int restante = usado(contaId, EscopoCota.DIARIA, dia).map(Contador::restante).orElse(0);
        if (periodoInicio != null) {
            restante = Math.min(restante, usado(contaId, EscopoCota.MENSAL, periodoInicio).map(Contador::restante).orElse(0));
        }
        return restante;
    }

    private LocalDate hoje() {
        return LocalDate.now(relogio.withZone(props.fuso()));
    }

    /** Cria a janela se não existir; INSERT IGNORE é seguro com chamadas simultâneas. */
    private void garantirJanela(String contaId, EscopoCota escopo, LocalDate inicio, LocalDate fim, int limite) {
        jdbc.sql("""
                INSERT IGNORE INTO cota_uso (conta_id, escopo, janela_inicio, janela_fim, limite, usado, excedente)
                VALUES (?, ?, ?, ?, ?, 0, 0)""")
                .params(contaId, escopo.name(), inicio, fim, limite).update();
    }

    private int incrementar(String contaId, EscopoCota escopo, LocalDate inicio) {
        return jdbc.sql("""
                UPDATE cota_uso SET usado = usado + 1
                 WHERE conta_id = ? AND escopo = ? AND janela_inicio = ? AND usado < limite""")
                .params(contaId, escopo.name(), inicio).update();
    }

    private record Contador(int limite, int usado, int excedente) {
        int restante() {
            return Math.max(limite - usado, 0);
        }
    }

    private Optional<Contador> usado(String contaId, EscopoCota escopo, LocalDate inicio) {
        return jdbc.sql("SELECT limite, usado, excedente FROM cota_uso WHERE conta_id = ? AND escopo = ? AND janela_inicio = ?")
                .params(contaId, escopo.name(), inicio)
                .query((rs, n) -> new Contador(rs.getInt(1), rs.getInt(2), rs.getInt(3)))
                .optional();
    }

    private ExcecaoGateway cotaExcedida(EscopoCota escopo, String mensagem, String contaId, LocalDate janela) {
        Map<String, Object> detalhes = new LinkedHashMap<>();
        detalhes.put("tipo", escopo);
        usado(contaId, escopo, janela).ifPresent(c -> {
            detalhes.put("limite", c.limite());
            detalhes.put("usado", c.usado());
        });
        return new ExcecaoGateway(HttpStatus.TOO_MANY_REQUESTS, CodigoErro.COTA_EXCEDIDA, mensagem, detalhes);
    }
}
