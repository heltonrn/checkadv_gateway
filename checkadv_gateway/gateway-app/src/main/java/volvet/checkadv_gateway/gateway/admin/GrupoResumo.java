package volvet.checkadv_gateway.gateway.admin;

import java.util.List;

/** Item de {@code GET /admin/grupos}; nunca contém segredo. */
public record GrupoResumo(
        String prefixo,
        String nome,
        String baseUrlInterna,
        boolean ativo,
        long quantidadeContas,
        List<String> chavesAtivas) {
}
