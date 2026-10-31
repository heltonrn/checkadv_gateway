package volvet.checkadv_gateway.gateway.meta;

import java.util.List;

import volvet.checkadv_gateway.contrato.conta.StatusTemplate;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;

/**
 * Tudo o que o Gateway pede à Meta (Graph API / WhatsApp Cloud API). Fase 1: só a
 * implementação simulada; a real entra na Fase 2 sem mudar quem usa esta interface.
 *
 * Nenhum método é chamado dentro de transação de banco.
 */
public interface MetaCliente {

    /** POST /{phone-number-id}/messages com o template. */
    ResultadoEnvio enviarTemplate(EnvioMeta envio);

    /** Troca o code do Embedded Signup por token, inscreve o app na WABA e registra o número. */
    ResultadoOnboarding concluirOnboarding(String code, String wabaId, String phoneNumberId);

    /** Cria o template na WABA se não existir e devolve o status atual. */
    SituacaoTemplate garantirTemplate(String token, String wabaId, String nome, String idioma);

    /** DELETE /{waba-id}/subscribed_apps. */
    void removerInscricao(String token, String wabaId);

    /** Dados para um envio; {@code token} e {@code telefone} já decifrados (nunca logar). */
    record EnvioMeta(String envioId, String phoneNumberId, String token, String telefone,
                     String templateNome, String idioma, List<String> corpo, String botaoUrl) {

        @Override
        public String toString() {
            return "EnvioMeta[envioId=" + envioId + ", phoneNumberId=" + phoneNumberId + ", template=" + templateNome + "]";
        }
    }

    /** Resultado de uma tentativa de envio; define a próxima transição no worker. */
    sealed interface ResultadoEnvio {

        /** 200 com wamid → ENVIADO. */
        record Aceito(String wamid) implements ResultadoEnvio {
        }

        /** 4xx definitivo → FALHOU. */
        record FalhaDefinitiva(CategoriaErroEnvio categoria, String codigoMeta, String mensagem) implements ResultadoEnvio {
        }

        /** 429, 130429 ou falha de conexão (a Meta não recebeu) → volta para a fila com espera. */
        record FalhaTemporaria(String codigoMeta, String motivo) implements ResultadoEnvio {
        }

        /** Timeout ou 5xx depois de conectar: pode ter sido enviado → INCERTO. */
        record Incerto(String motivo) implements ResultadoEnvio {
        }
    }

    record ResultadoOnboarding(String token, String numeroExibicao, String nomeVerificado,
                               String qualidade, String limiteMensagens) {

        @Override
        public String toString() {
            return "ResultadoOnboarding[numero=" + numeroExibicao + ", token=***]";
        }
    }

    record SituacaoTemplate(StatusTemplate status, String motivo) {
    }

    /** Falha ao falar com a Meta fora do fluxo de envio (onboarding, templates). */
    class MetaIndisponivelException extends RuntimeException {
        public MetaIndisponivelException(String mensagem) {
            super(mensagem);
        }
    }
}
