package volvet.checkadv_gateway.gateway.admin;

import java.util.UUID;

import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/** Rotas 25 e 26: suporte operacional. */
@RestController
@RequestMapping("/admin")
public class AdminOperacaoController {

    private final AdminService admin;

    public AdminOperacaoController(AdminService admin) {
        this.admin = admin;
    }

    /** Rota 25. Recoloca na fila um callback que terminou em CALLBACK_FALHOU. */
    @PostMapping("/callbacks/{eventoId}/reenviar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void reenviarCallback(@AuthenticationPrincipal ChaveAutenticada operador,
                                 @PathVariable UUID eventoId) {
        admin.reenviarCallback(operador.keyId(), eventoId);
    }

    /** Rota 26. Estado do WhatsApp, token (sem mostrá-lo), template, qualidade e fila da conta. */
    @GetMapping("/contas/{contaId}/diagnostico")
    public DiagnosticoConta diagnostico(@AuthenticationPrincipal ChaveAutenticada operador,
                                        @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId) {
        return admin.diagnostico(contaId);
    }
}
