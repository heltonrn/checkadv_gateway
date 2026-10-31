package volvet.checkadv_gateway.gateway.admin;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import volvet.checkadv_gateway.contrato.Formatos;
import volvet.checkadv_gateway.contrato.link.FormatoTokenLink;
import volvet.checkadv_gateway.gateway.seguranca.ChaveAutenticada;

/**
 * Rotas 20 a 24: grupos de instâncias e rotação de chaves. Só chave de OPERADOR e só
 * pela rede interna; toda chamada gera auditoria.
 */
@RestController
@RequestMapping("/admin/grupos")
public class AdminGrupoController {

    private final AdminService admin;

    public AdminGrupoController(AdminService admin) {
        this.admin = admin;
    }

    /** Rota 20. Cria o grupo e devolve a primeira chave (segredo mostrado uma vez). */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public GrupoCriado criar(@AuthenticationPrincipal ChaveAutenticada operador,
                             @Valid @RequestBody NovoGrupo grupo) {
        return admin.criarGrupo(operador.keyId(), grupo);
    }

    /** Rota 21. */
    @GetMapping
    public List<GrupoResumo> listar(@AuthenticationPrincipal ChaveAutenticada operador) {
        return admin.listarGrupos();
    }

    /** Rota 22. Ativa/desativa ou muda a URL interna. */
    @PatchMapping("/{prefixo}")
    public GrupoResumo alterar(@AuthenticationPrincipal ChaveAutenticada operador,
                               @PathVariable @Pattern(regexp = FormatoTokenLink.REGEX_PREFIXO) String prefixo,
                               @Valid @RequestBody AlteracaoGrupo alteracao) {
        return admin.alterarGrupo(operador.keyId(), prefixo, alteracao);
    }

    /** Rota 23. Nova chave; a antiga continua válida até ser revogada (rotação sem parada). */
    @PostMapping("/{prefixo}/chaves")
    @ResponseStatus(HttpStatus.CREATED)
    public ChaveGerada novaChave(@AuthenticationPrincipal ChaveAutenticada operador,
                                 @PathVariable @Pattern(regexp = FormatoTokenLink.REGEX_PREFIXO) String prefixo) {
        return admin.novaChave(operador.keyId(), prefixo);
    }

    /** Rota 24. Revoga uma chave depois que a instância já trocou para a nova. */
    @DeleteMapping("/{prefixo}/chaves/{keyId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revogarChave(@AuthenticationPrincipal ChaveAutenticada operador,
                             @PathVariable @Pattern(regexp = FormatoTokenLink.REGEX_PREFIXO) String prefixo,
                             @PathVariable @Pattern(regexp = Formatos.ID_OPACO) String keyId) {
        admin.revogarChave(operador.keyId(), prefixo, keyId);
    }
}
