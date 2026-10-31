package volvet.checkadv_gateway.gateway.grupo;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.callback.RegistroCallback;
import volvet.checkadv_gateway.contrato.callback.SituacaoCallback;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/**
 * Rotas 14 a 16. URL e chave de callback são do GRUPO (não da conta): o grupo vem da
 * chave autenticada, por isso não há identificador na rota.
 */
@RestController
@RequestMapping("/v1/grupo/callback")
public class GrupoCallbackController {

    private final GrupoService grupos;

    public GrupoCallbackController(GrupoService grupos) {
        this.grupos = grupos;
    }

    /** Rota 14. */
    @GetMapping
    public SituacaoCallback consultar(@AuthenticationPrincipal ChaveAutenticada chave) {
        return grupos.situacaoCallback(chave.grupoPrefixo());
    }

    /** Rota 15. Só HTTPS e host na lista permitida do grupo (anti-SSRF). */
    @PutMapping
    public SituacaoCallback registrar(@AuthenticationPrincipal ChaveAutenticada chave,
                                      @Valid @RequestBody RegistroCallback registro) {
        return grupos.registrarCallback(chave.grupoPrefixo(), registro.url());
    }

    /** Rota 16. Envia um evento teste.ping assinado. */
    @PostMapping("/teste")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void testar(@AuthenticationPrincipal ChaveAutenticada chave) {
        grupos.dispararTeste(chave.grupoPrefixo());
    }
}
