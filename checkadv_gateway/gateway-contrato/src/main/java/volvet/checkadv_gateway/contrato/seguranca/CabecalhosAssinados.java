package volvet.checkadv_gateway.contrato.seguranca;

import java.util.Map;

/** Valores dos headers de autenticação de uma requisição ou callback. */
public record CabecalhosAssinados(String keyId, long timestamp, String assinatura) {

    public Map<String, String> comoMapa() {
        return Map.of(
                CabecalhosGateway.KEY_ID, keyId,
                CabecalhosGateway.TIMESTAMP, Long.toString(timestamp),
                CabecalhosGateway.ASSINATURA, assinatura);
    }

    @Override
    public String toString() {
        return "CabecalhosAssinados[keyId=" + keyId + ", timestamp=" + timestamp + "]";
    }
}
