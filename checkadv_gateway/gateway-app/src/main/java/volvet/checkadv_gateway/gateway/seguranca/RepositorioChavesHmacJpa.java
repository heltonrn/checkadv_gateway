package volvet.checkadv_gateway.gateway.seguranca;

import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.cripto.CifradorAesGcm;

/**
 * Lê a chave a cada requisição (sem cache): revogação vale na hora em todas as réplicas.
 * Se o volume pedir, um cache curto (ex.: 30 s) entra aqui sem mudar o filtro.
 */
@Component
class RepositorioChavesHmacJpa implements RepositorioChavesHmac {

    private final ChaveHmacRepository repository;
    private final CifradorAesGcm cifrador;

    RepositorioChavesHmacJpa(ChaveHmacRepository repository, CifradorAesGcm cifrador) {
        this.repository = repository;
        this.cifrador = cifrador;
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<ChaveResolvida> buscarAtiva(String keyId) {
        return repository.findById(keyId)
                .filter(ChaveHmac::utilizavel)
                .map(c -> new ChaveResolvida(
                        c.getKeyId(),
                        c.getTipo(),
                        c.getGrupoPrefixo(),
                        new AssinadorHmac(cifrador.decifrar(
                                c.getSegredoCifrado(), c.getChaveCifraId(), ChaveHmac.contextoCifra(c.getKeyId())))));
    }
}
