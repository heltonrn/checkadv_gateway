package volvet.checkadv_gateway.contrato.envio;

/** Como a Meta classificou a mensagem; {@code cobravel} vem do webhook de status. */
public record Cobranca(CategoriaCobranca categoria, Boolean cobravel) {
}
