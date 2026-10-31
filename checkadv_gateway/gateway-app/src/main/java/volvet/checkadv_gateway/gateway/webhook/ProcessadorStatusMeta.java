package volvet.checkadv_gateway.gateway.webhook;

import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.envio.CategoriaCobranca;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.gateway.envio.EnvioWhatsapp;
import volvet.checkadv_gateway.gateway.envio.EnvioWhatsappRepository;
import volvet.checkadv_gateway.gateway.envio.TransicoesEnvio;
import volvet.checkadv_gateway.gateway.envio.TransicoesEnvio.Alteracao;

/**
 * Aplica um status de mensagem vindo da Meta (webhook {@code messages.statuses}).
 * Usado pelo webhook real e pela Meta simulada, para que os dois sigam exatamente a
 * mesma regra.
 *
 * Status fora de ordem são inofensivos: cada transição só vale a partir de origens
 * permitidas (um "delivered" atrasado não desfaz um LIDO).
 */
@Component
public class ProcessadorStatusMeta {

    private static final Logger log = LoggerFactory.getLogger(ProcessadorStatusMeta.class);

    private final EnvioWhatsappRepository envios;
    private final TransicoesEnvio transicoes;

    public ProcessadorStatusMeta(EnvioWhatsappRepository envios, TransicoesEnvio transicoes) {
        this.envios = envios;
        this.transicoes = transicoes;
    }

    /**
     * @param statusMeta sent | delivered | read | failed
     * @return false se o wamid não é conhecido (evento guardado para reprocessar)
     */
    @Transactional
    public boolean aplicar(String wamid, String statusMeta, CategoriaCobranca categoria, Boolean cobravel,
                           String codigoErro, String mensagemErro) {
        Optional<EnvioWhatsapp> envio = envios.findByWamid(wamid);
        if (envio.isEmpty()) {
            log.warn("Status '{}' para wamid desconhecido", statusMeta);
            return false;
        }
        String id = envio.get().getEnvioId();
        if (categoria != null || cobravel != null) {
            transicoes.atualizarCobranca(id, categoria, cobravel);
        }
        switch (statusMeta) {
            case "sent" -> { /* já gravado como ENVIADO quando a Meta devolveu o wamid */ }
            case "delivered" -> transicoes.aplicar(id, List.of(StatusEnvio.ENVIADO), StatusEnvio.ENTREGUE,
                    Alteracao.nenhuma());
            case "read" -> transicoes.aplicar(id, List.of(StatusEnvio.ENVIADO, StatusEnvio.ENTREGUE), StatusEnvio.LIDO,
                    Alteracao.nenhuma());
            case "failed" -> transicoes.aplicar(id, List.of(StatusEnvio.ENVIADO, StatusEnvio.ENTREGUE), StatusEnvio.FALHOU,
                    Alteracao.comErro(categoriaDoErro(codigoErro), mensagemErro, codigoErro));
            default -> log.info("Status da Meta ignorado: {}", statusMeta);
        }
        return true;
    }

    /** Códigos de erro da Cloud API → categoria do contrato. Conferir com payloads reais na Fase 2. */
    public static CategoriaErroEnvio categoriaDoErro(String codigo) {
        if (codigo == null) {
            return CategoriaErroEnvio.ERRO_META;
        }
        return switch (codigo) {
            case "131026" -> CategoriaErroEnvio.NUMERO_SEM_WHATSAPP;
            case "131042" -> CategoriaErroEnvio.PAGAMENTO_PENDENTE;
            case "131048", "131056", "130429" -> CategoriaErroEnvio.LIMITE_META_EXCEDIDO;
            case "132000", "132001", "132005", "132007", "132012", "132015", "132016" ->
                    CategoriaErroEnvio.TEMPLATE_NAO_APROVADO;
            case "190", "131031" -> CategoriaErroEnvio.CONTA_DESCONECTADA;
            default -> CategoriaErroEnvio.ERRO_META;
        };
    }
}
