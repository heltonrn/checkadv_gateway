package volvet.checkadv_gateway.gateway.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/** Template único usado para todos os escritórios (criado em cada WABA no onboarding). */
@ConfigurationProperties("app.whatsapp")
public record WhatsappProperties(
        @DefaultValue("comunicacao_registrada") String templateNome,
        @DefaultValue("pt_BR") String templateIdioma) {
}
