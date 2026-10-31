package volvet.checkadv_gateway.gateway.admin;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.gateway.comum.Auditoria;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.CallbackProperties;
import volvet.checkadv_gateway.gateway.conta.Conta;
import volvet.checkadv_gateway.gateway.conta.ContaRepository;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfig;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfigRepository;
import volvet.checkadv_gateway.gateway.cripto.ProtecaoDados;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;
import volvet.checkadv_gateway.gateway.grupo.GrupoInstancia;
import volvet.checkadv_gateway.gateway.grupo.GrupoInstanciaRepository;
import volvet.checkadv_gateway.gateway.seguranca.ChavesGrupo;
import volvet.checkadv_gateway.gateway.seguranca.TipoChave;

/** Operações do operador Volvet. Toda escrita é auditada com o keyId do operador. */
@Service
public class AdminService {

    private final GrupoInstanciaRepository grupos;
    private final ContaRepository contas;
    private final WhatsappContaConfigRepository whatsapp;
    private final ChavesGrupo chaves;
    private final ProtecaoDados protecao;
    private final Auditoria auditoria;
    private final CallbackProperties callbackProps;
    private final JdbcClient jdbc;
    private final Clock relogio;

    public AdminService(GrupoInstanciaRepository grupos, ContaRepository contas, WhatsappContaConfigRepository whatsapp,
                        ChavesGrupo chaves, ProtecaoDados protecao, Auditoria auditoria,
                        CallbackProperties callbackProps, JdbcClient jdbc, Clock relogio) {
        this.grupos = grupos;
        this.contas = contas;
        this.whatsapp = whatsapp;
        this.chaves = chaves;
        this.protecao = protecao;
        this.auditoria = auditoria;
        this.callbackProps = callbackProps;
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    @Transactional
    public GrupoCriado criarGrupo(String operador, NovoGrupo novo) {
        if (grupos.existsById(novo.prefixo())) {
            throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO, "Prefixo já em uso.");
        }
        GrupoInstancia g = grupos.saveAndFlush(GrupoInstancia.novo(novo.prefixo(), novo.nome(), novo.baseUrlInterna(),
                novo.hostsCallbackPermitidos(), relogio.instant()));
        ChavesGrupo.ChaveNova chave = chaves.gerar(TipoChave.GRUPO, g.getPrefixo(), chaves.proximoKeyId(g.getPrefixo()));
        auditoria.registrar(operador, "GRUPO_CRIADO", g.getPrefixo(), "chave " + chave.keyId());
        return new GrupoCriado(g.getPrefixo(), g.isAtivo(), new ChaveGerada(chave.keyId(), chave.segredoBase64()));
    }

    @Transactional(readOnly = true)
    public List<GrupoResumo> listarGrupos() {
        return grupos.findAll().stream().map(this::resumo).toList();
    }

    @Transactional
    public GrupoResumo alterarGrupo(String operador, String prefixo, AlteracaoGrupo alteracao) {
        GrupoInstancia g = buscarGrupo(prefixo);
        g.alterar(alteracao.ativo(), alteracao.baseUrlInterna(), relogio.instant());
        grupos.saveAndFlush(g);
        auditoria.registrar(operador, "GRUPO_ALTERADO", prefixo, alteracao.toString());
        return resumo(g);
    }

    @Transactional
    public ChaveGerada novaChave(String operador, String prefixo) {
        buscarGrupo(prefixo);
        ChavesGrupo.ChaveNova chave = chaves.gerar(TipoChave.GRUPO, prefixo, chaves.proximoKeyId(prefixo));
        auditoria.registrar(operador, "CHAVE_CRIADA", prefixo, chave.keyId());
        return new ChaveGerada(chave.keyId(), chave.segredoBase64());
    }

    /** Recusa revogar a última chave ativa: o grupo ficaria sem acesso e sem callbacks. */
    @Transactional
    public void revogarChave(String operador, String prefixo, String keyId) {
        buscarGrupo(prefixo);
        List<String> ativas = chaves.ativas(prefixo);
        if (!ativas.contains(keyId)) {
            throw ExcecaoGateway.naoEncontrado("Chave ativa não encontrada neste grupo.");
        }
        if (ativas.size() == 1) {
            throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO,
                    "É a única chave ativa do grupo; gere uma nova antes de revogar esta.");
        }
        chaves.revogar(prefixo, keyId);
        auditoria.registrar(operador, "CHAVE_REVOGADA", prefixo, keyId);
    }

    @Transactional
    public void reenviarCallback(String operador, UUID eventoId) {
        Instant agora = relogio.instant();
        int linhas = jdbc.sql("""
                UPDATE callback_pendente
                   SET status = 'PENDENTE', tentativas = 0, proxima_tentativa = ?, prazo_ate = ?, ultimo_erro = NULL
                 WHERE evento_id = ? AND status = 'CALLBACK_FALHOU'""")
                .params(Tempo.utc(agora), Tempo.utc(agora.plus(callbackProps.prazo())), eventoId.toString()).update();
        if (linhas == 0) {
            boolean existe = jdbc.sql("SELECT COUNT(*) FROM callback_pendente WHERE evento_id = ?")
                    .param(eventoId.toString()).query(Integer.class).single() > 0;
            throw existe
                    ? new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO, "O callback não está em CALLBACK_FALHOU.")
                    : ExcecaoGateway.naoEncontrado("Evento não encontrado.");
        }
        auditoria.registrar(operador, "CALLBACK_REENVIADO", eventoId.toString(), null);
    }

    @Transactional(readOnly = true)
    public DiagnosticoConta diagnostico(String contaId) {
        Conta conta = contas.findById(contaId).orElseThrow(() -> ExcecaoGateway.naoEncontrado("Conta não encontrada."));
        WhatsappContaConfig c = whatsapp.findById(contaId).orElse(null);
        boolean tokenValido = false;
        if (c != null && c.getTokenCifrado() != null) {
            try {
                protecao.decifrar(c.getTokenCifrado(), c.getChaveCifraId(), WhatsappContaConfig.contextoToken(contaId));
                tokenValido = true;
            } catch (IllegalStateException e) {
                tokenValido = false;
            }
        }
        Map<StatusEnvio, Long> fila = new EnumMap<>(StatusEnvio.class);
        jdbc.sql("SELECT status, COUNT(*) FROM envio_whatsapp WHERE conta_id = ? GROUP BY status")
                .param(contaId)
                .query((rs, n) -> Map.entry(StatusEnvio.valueOf(rs.getString(1)), rs.getLong(2)))
                .list().forEach(e -> fila.put(e.getKey(), e.getValue()));
        List<String> erros = jdbc.sql("""
                SELECT CONCAT(COALESCE(erro_codigo_meta, '-'), ' ', erro_categoria, ': ', COALESCE(erro_mensagem, ''))
                  FROM envio_whatsapp WHERE conta_id = ? AND erro_categoria IS NOT NULL
                 ORDER BY atualizado_em DESC LIMIT 5""")
                .param(contaId).query(String.class).list();
        return new DiagnosticoConta(contaId, conta.getGrupoPrefixo(),
                c == null ? StatusWhatsapp.NAO_CONECTADA : c.getStatus(), tokenValido,
                c == null ? null : c.getTemplateStatus(), c == null ? null : c.getQualidade(),
                c == null ? null : c.getLimiteMensagens(), fila, erros);
    }

    private GrupoInstancia buscarGrupo(String prefixo) {
        return grupos.findById(prefixo).orElseThrow(() -> ExcecaoGateway.naoEncontrado("Grupo não encontrado."));
    }

    private GrupoResumo resumo(GrupoInstancia g) {
        return new GrupoResumo(g.getPrefixo(), g.getNome(), g.getBaseUrlInterna(), g.isAtivo(),
                contas.countByGrupoPrefixo(g.getPrefixo()), chaves.ativas(g.getPrefixo()));
    }
}
