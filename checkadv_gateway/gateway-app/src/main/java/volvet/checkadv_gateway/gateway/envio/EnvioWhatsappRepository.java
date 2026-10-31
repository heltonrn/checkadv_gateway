package volvet.checkadv_gateway.gateway.envio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EnvioWhatsappRepository extends JpaRepository<EnvioWhatsapp, String> {

    Optional<EnvioWhatsapp> findByContaIdAndReferenciaExterna(String contaId, String referenciaExterna);

    /** Envio visível para o grupo (via conta). */
    @Query("""
            select e from EnvioWhatsapp e
             where e.envioId = :envioId
               and e.contaId in (select c.contaId from Conta c where c.grupoPrefixo = :grupo)
            """)
    Optional<EnvioWhatsapp> buscarDoGrupo(@Param("envioId") String envioId, @Param("grupo") String grupo);

    Optional<EnvioWhatsapp> findByWamid(String wamid);
}
