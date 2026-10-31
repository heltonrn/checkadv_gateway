package volvet.checkadv_gateway.gateway.conta;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface WhatsappContaConfigRepository extends JpaRepository<WhatsappContaConfig, String> {

    Optional<WhatsappContaConfig> findByPhoneNumberId(String phoneNumberId);
}
