package volvet.checkadv_gateway.gateway.seguranca;

import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;

/** Chave ativa já decifrada, pronta para conferir assinaturas. */
public record ChaveResolvida(String keyId, TipoChave tipo, String grupoPrefixo, AssinadorHmac assinador) {
}
