package volvet.checkadv_gateway.gateway.envio;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;

/** Linha do tempo exibida no GET do envio. Só estados notificáveis e o ENFILEIRADO inicial. */
@Entity
@Table(name = "envio_historico")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvioHistorico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "envio_id", nullable = false, length = 36)
    private String envioId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatusEnvio status;

    @Column(name = "sequencia", nullable = false)
    private int sequencia;

    @Column(name = "ocorrido_em", nullable = false)
    private Instant ocorridoEm;

    @Column(name = "detalhe", length = 255)
    private String detalhe;
}
