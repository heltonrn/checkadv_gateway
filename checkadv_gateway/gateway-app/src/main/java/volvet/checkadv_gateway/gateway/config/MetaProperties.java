package volvet.checkadv_gateway.gateway.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * @param modo     "simulado" (Fase 1) ou "cloud-api" (Fase 2, ainda não implementado)
 * @param simulado comportamento da Meta simulada
 */
@ConfigurationProperties("app.meta")
public record MetaProperties(
        @DefaultValue("simulado") String modo,
        @DefaultValue Simulado simulado) {

    /**
     * @param atrasoEntrega   depois do envio, quando chega o status "delivered"
     * @param atrasoLeitura   depois do envio, quando chega o status "read" (zero desliga)
     * @param aprovarTemplate aprova o template na hora (senão fica PENDENTE)
     */
    public record Simulado(
            @DefaultValue("2s") Duration atrasoEntrega,
            @DefaultValue("5s") Duration atrasoLeitura,
            @DefaultValue("true") boolean aprovarTemplate) {
    }
}
