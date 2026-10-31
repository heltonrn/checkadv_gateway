package volvet.checkadv_gateway.gateway.conta;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.conta.RegistroConta;
import volvet.checkadv_gateway.contrato.conta.SituacaoConta;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/** Rotas 5 e 6: registro e situação da conta (escritório) no grupo da chave. */
@RestController
@RequestMapping("/v1/contas/{contaId}")
public class ContaController {

    private final ContaService contas;

    public ContaController(ContaService contas) {
        this.contas = contas;
    }

    /**
     * Rota 5. Registra ou atualiza a conta no grupo da chave (idempotente).
     * 201 criada | 200 atualizada | 403 se já pertence a outro grupo.
     */
    @PutMapping
    public ResponseEntity<SituacaoConta> registrar(@AuthenticationPrincipal ChaveAutenticada chave,
                                                   @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId,
                                                   @Valid @RequestBody RegistroConta registro) {
        ContaService.Registro r = contas.registrar(chave.grupoPrefixo(), contaId, registro);
        return ResponseEntity.status(r.criada() ? HttpStatus.CREATED : HttpStatus.OK).body(r.situacao());
    }

    /** Rota 6. Situação do WhatsApp, do template e da cota. */
    @GetMapping
    public SituacaoConta situacao(@AuthenticationPrincipal ChaveAutenticada chave,
                                  @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId) {
        return contas.situacao(chave.grupoPrefixo(), contaId);
    }
}
