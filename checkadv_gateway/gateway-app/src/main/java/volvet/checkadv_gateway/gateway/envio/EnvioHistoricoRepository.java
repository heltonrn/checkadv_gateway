package volvet.checkadv_gateway.gateway.envio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EnvioHistoricoRepository extends JpaRepository<EnvioHistorico, Long> {

    List<EnvioHistorico> findByEnvioIdOrderByIdAsc(String envioId);
}
