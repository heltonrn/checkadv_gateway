package volvet.checkadv_gateway.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Gateway WhatsApp do CheckADV: único componente que fala com a Meta e guarda os
 * segredos (App Secret, tokens dos escritórios). Não tem front end.
 *
 * Decisão: não usar @ConfigurationPropertiesScan aqui; cada configuração habilita suas
 * próprias properties, para os testes de fatia (@WebMvcTest) não exigirem a chave mestra.
 */
@SpringBootApplication
public class CheckadvGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(CheckadvGatewayApplication.class, args);
    }
}
