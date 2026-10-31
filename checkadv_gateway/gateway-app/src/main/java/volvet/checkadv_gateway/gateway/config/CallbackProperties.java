package volvet.checkadv_gateway.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param permitirHttp aceita callback em http:// (só para desenvolvimento local)
 * @param prazo        tempo total de retentativas antes de CALLBACK_FALHOU
 */
@ConfigurationProperties("app.callback")
public record CallbackProperties(
        @DefaultValue("20") int lote,
        @DefaultValue("3s") Duration timeoutConexao,
        @DefaultValue("5s") Duration timeoutLeitura,
        @DefaultValue("false") boolean permitirHttp,
        @DefaultValue("24h") Duration prazo) {
}
