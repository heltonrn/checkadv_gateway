package volvet.checkadv_gateway.gateway.envio;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.EntityManager;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.envio.Cobranca;
import volvet.checkadv_gateway.contrato.envio.EnvioCriado;
import volvet.checkadv_gateway.contrato.envio.EnvioDetalhe;
import volvet.checkadv_gateway.contrato.envio.ErroEnvio;
import volvet.checkadv_gateway.contrato.envio.HistoricoStatus;
import volvet.checkadv_gateway.contrato.envio.NovoEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.contrato.link.FormatoTokenLink;
import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.conta.Conta;
import volvet.checkadv_gateway.gateway.conta.ContaService;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfig;
import volvet.checkadv_gateway.gateway.conta.WhatsappContaConfigRepository;
import volvet.checkadv_gateway.gateway.cota.ServicoCota;
import volvet.checkadv_gateway.gateway.cripto.ProtecaoDados;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;
import volvet.checkadv_gateway.gateway.optout.ServicoOptout;

/**
 * {@code POST /v1/envios}: valida, reserva a cota e grava na fila — tudo numa transação.
 * Não fala com a Meta: quem envia é o worker. Assim a resposta sai em milissegundos.
 *
 * Idempotência: chave {@code contaId + referenciaExterna}. Mesmo conteúdo devolve o envio
 * existente (200); conteúdo diferente é 409. Duas chamadas iguais ao mesmo tempo: a chave
 * única do banco deixa só uma gravar; a outra relê e devolve 200.
 */
@Service
public class EnvioService {

    private final ContaService contas;
    private final WhatsappContaConfigRepository whatsapp;
    private final EnvioWhatsappRepository envios;
    private final EnvioHistoricoRepository historicos;
    private final ServicoCota cota;
    private final ServicoOptout optout;
    private final TransicoesEnvio transicoes;
    private final ProtecaoDados protecao;
    private final JsonMapper json;
    private final TransactionTemplate tx;
    private final EntityManager em;
    private final Clock clock;

    public EnvioService(ContaService contas, WhatsappContaConfigRepository whatsapp, EnvioWhatsappRepository envios,
                        EnvioHistoricoRepository historicos, ServicoCota cota, ServicoOptout optout,
                        TransicoesEnvio transicoes, ProtecaoDados protecao, JsonMapper json,
                        TransactionTemplate tx, EntityManager em, Clock relogio) {
        this.contas = contas;
        this.whatsapp = whatsapp;
        this.envios = envios;
        this.historicos = historicos;
        this.cota = cota;
        this.optout = optout;
        this.transicoes = transicoes;
        this.protecao = protecao;
        this.json = json;
        this.tx = tx;
        this.em = em;
        this.clock = relogio;
    }

    /** @param novo     true → 202; false → 200 (repetição idempotente)
     *  @param restante cota restante (dia/período), para o header X-Cota-Restante; nulo na repetição */
    public record Resultado(EnvioCriado envio, boolean novo, Integer restante) {
    }

    public Resultado enfileirar(String grupo, NovoEnvio pedido) {
        String hash = hashConteudo(pedido);
        try {
            return tx.execute(s -> enfileirarNaTransacao(grupo, pedido, hash));
        } catch (DataIntegrityViolationException corrida) {
            // Outra chamada com a mesma referência gravou primeiro: relê e decide.
            return tx.execute(s -> envios.findByContaIdAndReferenciaExterna(pedido.contaId(), pedido.referenciaExterna())
                    .map(existente -> repeticao(existente, hash))
                    .orElseThrow(() -> corrida));
        }
    }

    private Resultado enfileirarNaTransacao(String grupo, NovoEnvio pedido, String hash) {
        Conta conta = contas.buscarDoGrupo(grupo, pedido.contaId());

        // Repetição vem antes das demais regras: um 202 perdido tem de ser recuperável
        // mesmo que o WhatsApp tenha sido desconectado nesse meio-tempo.
        Optional<EnvioWhatsapp> existente =
                envios.findByContaIdAndReferenciaExterna(pedido.contaId(), pedido.referenciaExterna());
        if (existente.isPresent()) {
            return repeticao(existente.get(), hash);
        }

        if (!conta.isAtiva()) {
            throw new ExcecaoGateway(HttpStatus.UNPROCESSABLE_CONTENT, CodigoErro.CONTA_INATIVA, "Conta desativada.");
        }
        String prefixoToken = FormatoTokenLink.prefixoDe(pedido.template().botaoUrl()).orElseThrow();
        if (!prefixoToken.equals(grupo)) {
            throw new ExcecaoGateway(HttpStatus.BAD_REQUEST, CodigoErro.PAYLOAD_INVALIDO,
                    "O token do link não pertence a este grupo.",
                    Map.of("campos", Map.of("template.botaoUrl", "prefixo diferente do grupo da chave")));
        }
        WhatsappContaConfig config = whatsapp.findById(conta.getContaId())
                .filter(c -> c.getStatus() == StatusWhatsapp.CONECTADA)
                .orElseThrow(() -> new ExcecaoGateway(HttpStatus.UNPROCESSABLE_CONTENT, CodigoErro.CONTA_SEM_WHATSAPP,
                        "Conta sem WhatsApp conectado."));
        if (!config.prontaParaEnviar(pedido.template().nome(), pedido.template().idioma())) {
            throw new ExcecaoGateway(HttpStatus.UNPROCESSABLE_CONTENT, CodigoErro.TEMPLATE_INDISPONIVEL,
                    "Template não aprovado para esta conta.",
                    Map.of("nome", pedido.template().nome(), "idioma", pedido.template().idioma()));
        }
        String telefone = pedido.destinatario().telefone();
        String telefoneHash = protecao.hashTelefone(telefone);
        if (optout.bloqueado(conta.getContaId(), telefoneHash)) {
            throw new ExcecaoGateway(HttpStatus.UNPROCESSABLE_CONTENT, CodigoErro.NUMERO_OPT_OUT,
                    "O destinatário pediu para não receber mensagens deste escritório.");
        }

        ServicoCota.Reserva reserva = cota.reservar(conta.getContaId());

        Instant agora = clock.instant();
        String envioId = UUID.randomUUID().toString();
        String parametros = json.writeValueAsString(
                new ParametrosTemplate(pedido.template().corpo(), pedido.template().botaoUrl()));
        EnvioWhatsapp envio = EnvioWhatsapp.novo(envioId, conta.getContaId(), pedido.referenciaExterna(), hash,
                protecao.cifrar(telefone, EnvioWhatsapp.contextoTelefone(envioId)), telefoneHash,
                ProtecaoDados.mascararTelefone(telefone), pedido.template().nome(), pedido.template().idioma(),
                protecao.cifrar(parametros, EnvioWhatsapp.contextoParametros(envioId)),
                reserva.excedente(), reserva.dia(), reserva.periodoInicio(), agora);
        envios.saveAndFlush(envio);
        em.createNativeQuery("INSERT INTO envio_historico (envio_id, status, sequencia, ocorrido_em) VALUES (?, ?, 0, ?)")
                .setParameter(1, envioId).setParameter(2, StatusEnvio.ENFILEIRADO.name())
                .setParameter(3, Tempo.utc(agora)).executeUpdate();

        return new Resultado(criado(envio), true, reserva.restante());
    }

    private Resultado repeticao(EnvioWhatsapp existente, String hash) {
        if (!existente.getHashConteudo().equals(hash)) {
            throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.CONFLITO_IDEMPOTENCIA,
                    "referenciaExterna já usada com outro conteúdo; para reenviar, use uma referência nova (ex.: #2).",
                    Map.of("envioId", existente.getEnvioId()));
        }
        return new Resultado(criado(existente), false, null);
    }

    @Transactional(readOnly = true)
    public EnvioDetalhe buscar(String grupo, UUID envioId) {
        return detalhe(envios.buscarDoGrupo(envioId.toString(), grupo)
                .orElseThrow(() -> ExcecaoGateway.naoEncontrado("Envio não encontrado.")));
    }

    @Transactional(readOnly = true)
    public EnvioDetalhe buscarPorReferencia(String grupo, String contaId, String referenciaExterna) {
        contas.buscarDoGrupo(grupo, contaId);
        return detalhe(envios.findByContaIdAndReferenciaExterna(contaId, referenciaExterna)
                .orElseThrow(() -> ExcecaoGateway.naoEncontrado("Envio não encontrado.")));
    }

    /** Só ENFILEIRADO pode ser cancelado (depois disso a Meta pode já ter recebido). */
    @Transactional
    public EnvioDetalhe cancelar(String grupo, UUID envioId) {
        EnvioWhatsapp envio = envios.buscarDoGrupo(envioId.toString(), grupo)
                .orElseThrow(() -> ExcecaoGateway.naoEncontrado("Envio não encontrado."));
        boolean cancelado = transicoes.aplicar(envio.getEnvioId(), List.of(StatusEnvio.ENFILEIRADO),
                StatusEnvio.CANCELADO, new TransicoesEnvio.Alteracao(null, null, null, null, null, "Cancelado pela instância"));
        em.refresh(envio);
        if (!cancelado) {
            throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO,
                    "Só envios ENFILEIRADOS podem ser cancelados.", Map.of("status", envio.getStatus()));
        }
        return detalhe(envio);
    }

    private EnvioDetalhe detalhe(EnvioWhatsapp e) {
        List<HistoricoStatus> historico = historicos.findByEnvioIdOrderByIdAsc(e.getEnvioId()).stream()
                .map(h -> new HistoricoStatus(h.getStatus(), Tempo.paraApi(h.getOcorridoEm())))
                .toList();
        ErroEnvio erro = e.getErroCategoria() == null ? null
                : new ErroEnvio(e.getErroCategoria(), e.getErroMensagem(), e.getErroCodigoMeta());
        Cobranca cobranca = e.getCategoriaCobranca() == null && e.getCobravel() == null ? null
                : new Cobranca(e.getCategoriaCobranca(), e.getCobravel());
        return new EnvioDetalhe(UUID.fromString(e.getEnvioId()), e.getContaId(), e.getReferenciaExterna(),
                e.getStatus(), e.getSequencia(), new EnvioDetalhe.DestinatarioMascarado(e.getTelefoneMascarado()),
                new EnvioDetalhe.TemplateResumo(e.getTemplateNome(), e.getTemplateIdioma()), cobranca, erro, historico,
                Tempo.paraApi(e.getCriadoEm()), Tempo.paraApi(e.getAtualizadoEm()));
    }

    private static EnvioCriado criado(EnvioWhatsapp e) {
        return new EnvioCriado(UUID.fromString(e.getEnvioId()), e.getReferenciaExterna(), e.getStatus(),
                e.getSequencia(), Tempo.paraApi(e.getCriadoEm()));
    }

    /** SHA-256 do conteúdo normalizado; separadores de controle evitam ambiguidade entre campos. */
    static String hashConteudo(NovoEnvio p) {
        String canonico = String.join("\u001F",
                p.destinatario().telefone(), p.template().nome(), p.template().idioma(),
                String.join("\u001E", p.template().corpo()), p.template().botaoUrl());
        return AssinadorHmac.sha256Hex(canonico.getBytes(StandardCharsets.UTF_8));
    }
}
