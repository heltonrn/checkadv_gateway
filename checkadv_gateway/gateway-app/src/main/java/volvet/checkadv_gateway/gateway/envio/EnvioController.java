package volvet.checkadv_gateway.gateway.envio;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.envio.EnvioCriado;
import volvet.checkadv_gateway.contrato.envio.EnvioDetalhe;
import volvet.checkadv_gateway.contrato.envio.NovoEnvio;
import volvet.checkadv_gateway.contrato.seguranca.CabecalhosGateway;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/**
 * Rotas 1 a 4 do contrato: fila de envio de templates.
 * Toda operação enxerga só contas do grupo da chave ({@code chave.grupoPrefixo()}).
 */
@RestController
@RequestMapping("/v1/envios")
public class EnvioController {

    private final EnvioService envios;

    public EnvioController(EnvioService envios) {
        this.envios = envios;
    }

    /**
     * Rota 1. Enfileira um template; idempotente por contaId + referenciaExterna.
     * 202 novo (Location + X-Cota-Restante) | 200 repetição com mesmo conteúdo | 409 conteúdo diferente.
     */
    @PostMapping
    public ResponseEntity<EnvioCriado> enfileirar(@AuthenticationPrincipal ChaveAutenticada chave,
                                                  @Valid @RequestBody NovoEnvio envio) {
        EnvioService.Resultado r = envios.enfileirar(chave.grupoPrefixo(), envio);
        if (!r.novo()) {
            return ResponseEntity.ok(r.envio());
        }
        return ResponseEntity.accepted()
                .location(URI.create("/v1/envios/" + r.envio().envioId()))
                .header(CabecalhosGateway.COTA_RESTANTE, String.valueOf(r.restante()))
                .body(r.envio());
    }

    /** Rota 2. Consulta por ID. */
    @GetMapping("/{envioId}")
    public EnvioDetalhe buscarPorId(@AuthenticationPrincipal ChaveAutenticada chave,
                                    @PathVariable UUID envioId) {
        return envios.buscar(chave.grupoPrefixo(), envioId);
    }

    /** Rota 3. Consulta pela chave de idempotência (reconciliação quando o 202 se perdeu). */
    @GetMapping
    public EnvioDetalhe buscarPorReferencia(
            @AuthenticationPrincipal ChaveAutenticada chave,
            @RequestParam @Pattern(regexp = Formatos.ID_OPACO) String contaId,
            @RequestParam @Pattern(regexp = Formatos.REFERENCIA_EXTERNA) String referenciaExterna) {
        return envios.buscarPorReferencia(chave.grupoPrefixo(), contaId, referenciaExterna);
    }

    /** Rota 4. Cancela envio ainda ENFILEIRADO e libera a cota; fora disso, 409 ESTADO_INVALIDO. */
    @PostMapping("/{envioId}/cancelar")
    public EnvioDetalhe cancelar(@AuthenticationPrincipal ChaveAutenticada chave,
                                 @PathVariable UUID envioId) {
        return envios.cancelar(chave.grupoPrefixo(), envioId);
    }
}
