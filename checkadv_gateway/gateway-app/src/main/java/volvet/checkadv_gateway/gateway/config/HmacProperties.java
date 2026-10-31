package volvet.checkadv_gateway.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param janelaSegundos    tolerância do X-Timestamp (±)
 * @param corpoMaximoBytes  limite do corpo nas rotas assinadas
 */
@ConfigurationProperties("app.hmac")
public record HmacProperties(
        @DefaultValue("300") long janelaSegundos,
        @DefaultValue("8192") int corpoMaximoBytes) {
}
