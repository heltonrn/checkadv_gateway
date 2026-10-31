package volvet.checkadv_gateway.gateway.conta;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import volvet.checkadv_gateway.contrato.callback.DadosContaWhatsapp;
import volvet.checkadv_gateway.contrato.callback.DadosTemplateStatus;
import volvet.checkadv_gateway.contrato.callback.TipoEvento;
import volvet.checkadv_gateway.contrato.conta.OnboardingWhatsapp;
import volvet.checkadv_gateway.contrato.conta.SituacaoConta;
import volvet.checkadv_gateway.contrato.conta.StatusTemplate;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.gateway.callback.PublicadorEventos;
import volvet.checkadv_gateway.gateway.config.WhatsappProperties;
import volvet.checkadv_gateway.gateway.cripto.ProtecaoDados;
import volvet.checkadv_gateway.gateway.envio.TransicoesEnvio;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;
import volvet.checkadv_gateway.gateway.meta.MetaCliente;
import volvet.checkadv_gateway.gateway.meta.MetaCliente.ResultadoOnboarding;
import volvet.checkadv_gateway.gateway.meta.MetaCliente.SituacaoTemplate;

/**
 * Número WhatsApp do escritório: onboarding (Embedded Signup), desconexão e template.
 *
 * Decisão: as chamadas à Meta ficam FORA de transação (podem demorar); o banco é
 * atualizado antes e depois em transações curtas. Na Fase 1, com a Meta simulada, tudo
 * termina dentro da própria requisição; o contrato já prevê 202 + callback para quando
 * isso virar assíncrono com a Meta real.
 */
@Service
public class WhatsappContaService {

    private static final Logger log = LoggerFactory.getLogger(WhatsappContaService.class);

    private final ContaService contas;
    private final WhatsappContaConfigRepository configs;
    private final MetaCliente meta;
    private final ProtecaoDados protecao;
    private final PublicadorEventos eventos;
    private final TransicoesEnvio transicoes;
    private final WhatsappProperties props;
    private final JdbcClient jdbc;
    private final TransactionTemplate tx;
    private final Clock relogio;

    public WhatsappContaService(ContaService contas, WhatsappContaConfigRepository configs, MetaCliente meta,
                                ProtecaoDados protecao, PublicadorEventos eventos, TransicoesEnvio transicoes,
                                WhatsappProperties props, JdbcClient jdbc, TransactionTemplate tx, Clock relogio) {
        this.contas = contas;
        this.configs = configs;
        this.meta = meta;
        this.protecao = protecao;
        this.eventos = eventos;
        this.transicoes = transicoes;
        this.props = props;
        this.jdbc = jdbc;
        this.tx = tx;
        this.relogio = relogio;
    }

    public SituacaoConta onboarding(String grupo, String contaId, OnboardingWhatsapp dados) {
        // 1. Marca CONECTANDO (curto, com as validações de conflito)
        tx.executeWithoutResult(s -> {
            Conta conta = contas.buscarDoGrupo(grupo, contaId);
            Optional<WhatsappContaConfig> doNumero = configs.findByPhoneNumberId(dados.phoneNumberId());
            if (doNumero.isPresent() && !doNumero.get().getContaId().equals(conta.getContaId())) {
                throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO,
                        "Este número já está conectado a outra conta.");
            }
            WhatsappContaConfig config = configs.findById(contaId)
                    .orElseGet(() -> WhatsappContaConfig.nova(contaId, relogio.instant()));
            if (config.getStatus() == StatusWhatsapp.CONECTADA
                    && !dados.phoneNumberId().equals(config.getPhoneNumberId())) {
                throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO,
                        "Conta já conectada a outro número; desconecte antes.");
            }
            config.iniciarConexao(dados.wabaId(), dados.phoneNumberId(), relogio.instant());
            configs.save(config);
        });

        // 2. Fala com a Meta (fora de transação)
        ResultadoOnboarding resultado;
        SituacaoTemplate template;
        try {
            resultado = meta.concluirOnboarding(dados.code(), dados.wabaId(), dados.phoneNumberId());
            template = meta.garantirTemplate(resultado.token(), dados.wabaId(), props.templateNome(), props.templateIdioma());
        } catch (MetaCliente.MetaIndisponivelException e) {
            log.warn("Onboarding da conta {} falhou: {}", contaId, e.getMessage());
            tx.executeWithoutResult(s -> {
                WhatsappContaConfig config = configs.findById(contaId).orElseThrow();
                config.falharConexao(relogio.instant());
                publicarConta(grupo, contaId, config, "Onboarding falhou: " + e.getMessage());
            });
            throw new ExcecaoGateway(HttpStatus.SERVICE_UNAVAILABLE, CodigoErro.META_INDISPONIVEL,
                    "Não foi possível concluir a conexão com a Meta.");
        }

        // 3. Grava o resultado e avisa a instância
        return tx.execute(s -> {
            Conta conta = contas.buscarDoGrupo(grupo, contaId);
            WhatsappContaConfig config = configs.findById(contaId).orElseThrow();
            config.conectar(protecao.cifrar(resultado.token(), WhatsappContaConfig.contextoToken(contaId)),
                    protecao.chaveCifraId(), resultado.numeroExibicao(),
                    resultado.nomeVerificado() != null ? resultado.nomeVerificado() : conta.getNomeExibicao(),
                    resultado.qualidade(), resultado.limiteMensagens(), relogio.instant());
            config.atualizarTemplate(props.templateNome(), props.templateIdioma(), template.status(), template.motivo(),
                    relogio.instant());
            configs.saveAndFlush(config);
            publicarConta(grupo, contaId, config, "Onboarding concluído");
            eventos.publicar(grupo, contaId, TipoEvento.TEMPLATE_STATUS_ALTERADO,
                    new DadosTemplateStatus(config.getTemplateNome(), config.getTemplateIdioma(), template.status(),
                            template.motivo()), null, null);
            return contas.montar(conta);
        });
    }

    /** Cancela a fila da conta, apaga o token e marca DESCONECTADA. */
    public void desconectar(String grupo, String contaId) {
        Optional<String[]> credenciais = tx.execute(s -> {
            contas.buscarDoGrupo(grupo, contaId);
            WhatsappContaConfig config = configs.findById(contaId)
                    .filter(c -> c.getStatus() != StatusWhatsapp.NAO_CONECTADA && c.getStatus() != StatusWhatsapp.DESCONECTADA)
                    .orElseThrow(() -> new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO,
                            "Conta não está conectada."));
            String token = config.getTokenCifrado() == null ? null
                    : protecao.decifrar(config.getTokenCifrado(), config.getChaveCifraId(),
                    WhatsappContaConfig.contextoToken(contaId));
            String wabaId = config.getWabaId();
            cancelarFila(contaId);
            config.desconectar(StatusWhatsapp.DESCONECTADA, relogio.instant());
            publicarConta(grupo, contaId, config, "Desconectada pela instância");
            return token == null ? Optional.<String[]>empty() : Optional.of(new String[] {token, wabaId});
        });
        credenciais.ifPresent(c -> {
            try {
                meta.removerInscricao(c[0], c[1]);
            } catch (RuntimeException e) {
                log.warn("Conta {} desconectada, mas a remoção da inscrição na WABA falhou", contaId, e);
            }
        });
    }

    /** Recria/reconsulta o template na WABA da conta. */
    public void sincronizarTemplate(String grupo, String contaId) {
        String[] credenciais = tx.execute(s -> {
            contas.buscarDoGrupo(grupo, contaId);
            WhatsappContaConfig config = configs.findById(contaId)
                    .filter(c -> c.getStatus() == StatusWhatsapp.CONECTADA)
                    .orElseThrow(() -> new ExcecaoGateway(HttpStatus.UNPROCESSABLE_CONTENT, CodigoErro.CONTA_SEM_WHATSAPP,
                            "Conta sem WhatsApp conectado."));
            return new String[] {protecao.decifrar(config.getTokenCifrado(), config.getChaveCifraId(),
                    WhatsappContaConfig.contextoToken(contaId)), config.getWabaId()};
        });
        SituacaoTemplate situacao;
        try {
            situacao = meta.garantirTemplate(credenciais[0], credenciais[1], props.templateNome(), props.templateIdioma());
        } catch (MetaCliente.MetaIndisponivelException e) {
            throw new ExcecaoGateway(HttpStatus.SERVICE_UNAVAILABLE, CodigoErro.META_INDISPONIVEL,
                    "Não foi possível consultar o template na Meta.");
        }
        tx.executeWithoutResult(s -> {
            WhatsappContaConfig config = configs.findById(contaId).orElseThrow();
            StatusTemplate anterior = config.getTemplateStatus();
            config.atualizarTemplate(props.templateNome(), props.templateIdioma(), situacao.status(), situacao.motivo(),
                    relogio.instant());
            if (anterior != situacao.status()) {
                eventos.publicar(grupo, contaId, TipoEvento.TEMPLATE_STATUS_ALTERADO,
                        new DadosTemplateStatus(props.templateNome(), props.templateIdioma(), situacao.status(),
                                situacao.motivo()), null, null);
            }
        });
    }

    private void cancelarFila(String contaId) {
        List<String> enfileirados = jdbc.sql("SELECT envio_id FROM envio_whatsapp WHERE conta_id = ? AND status = 'ENFILEIRADO'")
                .param(contaId).query(String.class).list();
        enfileirados.forEach(id -> transicoes.aplicar(id, List.of(StatusEnvio.ENFILEIRADO), StatusEnvio.CANCELADO,
                new TransicoesEnvio.Alteracao(null, null, null, null, null, "WhatsApp desconectado")));
        if (!enfileirados.isEmpty()) {
            log.info("Conta {} desconectada: {} envio(s) cancelado(s)", contaId, enfileirados.size());
        }
    }

    private void publicarConta(String grupo, String contaId, WhatsappContaConfig config, String motivo) {
        eventos.publicar(grupo, contaId, TipoEvento.CONTA_WHATSAPP_ALTERADA,
                new DadosContaWhatsapp(config.getStatus(), config.getQualidade(), config.getLimiteMensagens(), motivo),
                null, null);
    }
}
