package volvet.checkadv_gateway.gateway.config;

import java.time.ZoneId;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param fuso               fuso das janelas de cota (dia e período do plano)
 * @param limiteDiarioPadrao limite diário de uma conta sem cota definida; abaixo do
 *                           tier inicial da Meta (250 destinatários únicos em 24 h)
 */
@ConfigurationProperties("app.cota")
public record CotaProperties(
        @DefaultValue("America/Sao_Paulo") ZoneId fuso,
        @DefaultValue("200") int limiteDiarioPadrao) {
}
