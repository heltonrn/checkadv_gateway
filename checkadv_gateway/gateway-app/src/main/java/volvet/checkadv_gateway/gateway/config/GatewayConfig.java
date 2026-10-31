package volvet.checkadv_gateway.gateway.config;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import volvet.checkadv_gateway.gateway.cripto.CifradorAesGcm;

@Configuration
@EnableConfigurationProperties({CriptoProperties.class, CotaProperties.class, WhatsappProperties.class,
        WorkerProperties.class, CallbackProperties.class, MetaProperties.class})
public class GatewayConfig {

    /** Relógio injetável: janela do HMAC, leases e expirações ficam testáveis. */
    @Bean
    Clock relogio() {
        return Clock.systemUTC();
    }

    @Bean
    CifradorAesGcm cifrador(CriptoProperties props) {
        return new CifradorAesGcm(props.chaveMestraId(), props.chaveMestra());
    }
}
