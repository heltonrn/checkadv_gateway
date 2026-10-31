package volvet.checkadv_gateway.gateway.conta;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ContaRepository extends JpaRepository<Conta, String> {

    /** Toda busca vinda da API v1 passa por aqui: conta de outro grupo "não existe". */
    Optional<Conta> findByContaIdAndGrupoPrefixo(String contaId, String grupoPrefixo);

    long countByGrupoPrefixo(String grupoPrefixo);
}
