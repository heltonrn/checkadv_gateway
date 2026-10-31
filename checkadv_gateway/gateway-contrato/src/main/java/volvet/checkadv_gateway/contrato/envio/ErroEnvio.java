package volvet.checkadv_gateway.contrato.envio;

/** Falha de entrega. {@code codigoMeta} é o código de erro devolvido pela Graph API, quando houver. */
public record ErroEnvio(CategoriaErroEnvio categoria, String mensagem, String codigoMeta) {
}
