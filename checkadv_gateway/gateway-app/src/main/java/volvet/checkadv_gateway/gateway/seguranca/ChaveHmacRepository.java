package volvet.checkadv_gateway.gateway.seguranca;

import org.springframework.data.jpa.repository.JpaRepository;

public interface ChaveHmacRepository extends JpaRepository<ChaveHmac, String> {
}
