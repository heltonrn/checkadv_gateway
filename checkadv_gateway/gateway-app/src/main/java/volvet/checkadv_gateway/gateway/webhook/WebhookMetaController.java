package volvet.checkadv_gateway.gateway.webhook;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;

/**
 * Rotas 17 e 18: único endpoint para todas as WABAs (um app, um App Secret).
 * Hostname público, separado do hostname da API v1.
 */
@RestController
@RequestMapping("/webhook/meta")
public class WebhookMetaController {

    public static final String CABECALHO_ASSINATURA = "X-Hub-Signature-256";

    /**
     * Rota 17. Verificação na configuração do webhook: se {@code hub.verify_token} confere
     * (tempo constante), devolve {@code hub.challenge} em texto puro; senão, 403.
     * Sem {@code produces = "text/plain"} no mapeamento: ele impediria o corpo JSON das
     * respostas de erro; o tipo será definido no ResponseEntity da implementação.
     */
    @GetMapping
    public String verificar(@RequestParam(name = "hub.mode", required = false) String modo,
                            @RequestParam(name = "hub.verify_token", required = false) String verifyToken,
                            @RequestParam(name = "hub.challenge", required = false) String desafio) {
        throw ExcecaoGateway.naoImplementado("GET /webhook/meta");
    }

    /**
     * Rota 18. Corpo lido como BYTES: a assinatura é calculada sobre o corpo bruto, antes
     * de qualquer parse. Ordem prevista: validar assinatura → gravar em webhook_evento
     * (dedup) → responder 200 → processar de forma assíncrona.
     */
    @PostMapping
    public void receber(@RequestHeader(name = CABECALHO_ASSINATURA, required = false) String assinatura,
                        @RequestBody(required = false) byte[] corpo) {
        throw ExcecaoGateway.naoImplementado("POST /webhook/meta");
    }
}
