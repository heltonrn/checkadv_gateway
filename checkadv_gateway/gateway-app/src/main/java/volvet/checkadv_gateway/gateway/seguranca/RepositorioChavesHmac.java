package volvet.checkadv_gateway.gateway.seguranca;

import java.util.Optional;

/** Busca chaves HMAC utilizáveis. Interface para os testes não dependerem do banco. */
@FunctionalInterface
public interface RepositorioChavesHmac {

    Optional<ChaveResolvida> buscarAtiva(String keyId);
}
