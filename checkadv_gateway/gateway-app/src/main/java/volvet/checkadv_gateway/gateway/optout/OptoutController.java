package volvet.checkadv_gateway.gateway.optout;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.optout.ConsultaOptout;
import volvet.checkadv_gateway.contrato.optout.RemocaoOptout;
import volvet.checkadv_gateway.contrato.optout.SituacaoOptout;
import volvet.checkadv_gateway.gateway.conta.ContaService;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/**
 * Rotas 12 e 13. POST mesmo para consulta: o telefone vai no corpo, nunca na URL
 * (URLs acabam em logs de proxy). Entrada no MVP ainda em aberto.
 */
@RestController
@RequestMapping("/v1/contas/{contaId}/optouts")
public class OptoutController {

    private final ContaService contas;
    private final ServicoOptout optout;

    public OptoutController(ContaService contas, ServicoOptout optout) {
        this.contas = contas;
        this.optout = optout;
    }

    /** Rota 12. O telefone está bloqueado para esta conta? */
    @PostMapping("/consulta")
    public SituacaoOptout consultar(@AuthenticationPrincipal ChaveAutenticada chave,
                                    @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId,
                                    @Valid @RequestBody ConsultaOptout consulta) {
        contas.buscarDoGrupo(chave.grupoPrefixo(), contaId);
        return optout.consultar(contaId, consulta.telefone());
    }

    /** Rota 13. Retira o opt-out após novo consentimento (auditado). */
    @PostMapping("/remocao")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@AuthenticationPrincipal ChaveAutenticada chave,
                        @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId,
                        @Valid @RequestBody RemocaoOptout remocao) {
        contas.buscarDoGrupo(chave.grupoPrefixo(), contaId);
        if (!optout.remover(chave.keyId(), contaId, remocao)) {
            throw ExcecaoGateway.naoEncontrado("Não há opt-out ativo para este telefone.");
        }
    }
}
