package volvet.checkadv_gateway.resolvedor;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Resolvedor de links do CheckADV: recebe {@code /v/{token}}, identifica o grupo pelos
 * 4 primeiros caracteres e repassa (proxy, não redirect) à instância do grupo.
 *
 * Não tem segredo da Meta nem lógica de verificação: a página é gerada pela instância.
 * Esqueleto; o proxy entra depois que a fonte do roteamento for decidida
 * (view v_roteamento ou rota /interno/roteamento do Gateway).
 */
@SpringBootApplication
public class ResolvedorApplication {

    public static void main(String[] args) {
        SpringApplication.run(ResolvedorApplication.class, args);
    }
}
