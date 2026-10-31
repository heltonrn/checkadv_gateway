package volvet.checkadv_gateway.gateway.admin;

/** Chave HMAC recém-criada. O segredo é devolvido UMA única vez e nunca mais pode ser lido. */
public record ChaveGerada(String keyId, String segredo) {

    @Override
    public String toString() {
        return "ChaveGerada[keyId=" + keyId + ", segredo=***]";
    }
}
