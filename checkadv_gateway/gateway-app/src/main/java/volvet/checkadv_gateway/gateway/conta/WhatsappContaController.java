package volvet.checkadv_gateway.gateway.conta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.conta.OnboardingWhatsapp;
import volvet.checkadv_gateway.contrato.conta.SituacaoConta;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/**
 * Rotas 7 a 9: número WhatsApp do escritório. Todas assíncronas (202); o resultado
 * chega por callback ({@code conta.whatsapp_alterada} / {@code template.status_alterado}).
 */
@RestController
@RequestMapping("/v1/contas/{contaId}/whatsapp")
public class WhatsappContaController {

    private final WhatsappContaService whatsapp;

    public WhatsappContaController(WhatsappContaService whatsapp) {
        this.whatsapp = whatsapp;
    }

    /** Rota 7. Conclui o Embedded Signup: troca o code por token, inscreve o app, registra o número e cria o template. */
    @PostMapping("/onboarding")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public SituacaoConta onboarding(@AuthenticationPrincipal ChaveAutenticada chave,
                                    @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId,
                                    @Valid @RequestBody OnboardingWhatsapp dados) {
        return whatsapp.onboarding(chave.grupoPrefixo(), contaId, dados);
    }

    /** Rota 8. Desconecta: cancela a fila da conta, apaga o token cifrado, marca DESCONECTADA. */
    @DeleteMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void desconectar(@AuthenticationPrincipal ChaveAutenticada chave,
                            @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId) {
        whatsapp.desconectar(chave.grupoPrefixo(), contaId);
    }

    /** Rota 9. Recria o template se não existir e reconsulta o status na Meta. */
    @PostMapping("/templates/sincronizar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void sincronizarTemplates(@AuthenticationPrincipal ChaveAutenticada chave,
                                     @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId) {
        whatsapp.sincronizarTemplate(chave.grupoPrefixo(), contaId);
    }
}
