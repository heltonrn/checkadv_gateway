package volvet.checkadv_gateway.gateway.cota;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.conta.DefinicaoCota;
import volvet.checkadv_gateway.contrato.conta.UsoCota;
import volvet.checkadv_gateway.gateway.conta.ContaService;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/** Rotas 10 e 11. O plano vive no CheckADV; o Gateway só aplica os limites de forma atômica. */
@RestController
@RequestMapping("/v1/contas/{contaId}")
public class CotaController {

    private final ContaService contas;
    private final ServicoCota cota;

    public CotaController(ContaService contas, ServicoCota cota) {
        this.contas = contas;
        this.cota = cota;
    }

    /** Rota 10. Define período (fuso America/Sao_Paulo, fim inclusivo), limites e política de excesso. */
    @PutMapping("/cota")
    public UsoCota definir(@AuthenticationPrincipal ChaveAutenticada chave,
                           @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId,
                           @Valid @RequestBody DefinicaoCota cota) {
        contas.buscarDoGrupo(chave.grupoPrefixo(), contaId);
        return this.cota.definir(contaId, cota);
    }

    /** Rota 11. Uso no período e no dia, e destinatários únicos nas últimas 24 h. */
    @GetMapping("/uso")
    public UsoCota uso(@AuthenticationPrincipal ChaveAutenticada chave,
                       @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String contaId) {
        contas.buscarDoGrupo(chave.grupoPrefixo(), contaId);
        return cota.uso(contaId);
    }
}
