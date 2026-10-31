package volvet.checkadv_gateway.gateway.seguranca;

/**
 * Principal das rotas assinadas. Os serviços usam {@code grupoPrefixo} para limitar
 * as contas visíveis (chave de um grupo só enxerga contas do próprio grupo).
 */
public record ChaveAutenticada(String keyId, TipoChave tipo, String grupoPrefixo) {
}
