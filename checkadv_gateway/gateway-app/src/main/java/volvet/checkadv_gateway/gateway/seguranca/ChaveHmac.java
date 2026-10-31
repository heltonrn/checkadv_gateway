package volvet.checkadv_gateway.gateway.seguranca;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Chave HMAC de um grupo ou de operador. O segredo é guardado cifrado (AES-GCM com a
 * chave mestra, AAD = {@code chave_hmac:<keyId>}) porque o Gateway precisa dele em claro
 * para recalcular assinaturas; hash não serviria.
 *
 * Duas chaves ativas do mesmo grupo podem coexistir durante a rotação.
 */
@Entity
@Table(name = "chave_hmac")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ChaveHmac {

    @Id
    @Column(name = "key_id", length = 64)
    private String keyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 16)
    private TipoChave tipo;

    /** Nulo para chaves de operador. */
    @Column(name = "grupo_prefixo", length = 4)
    private String grupoPrefixo;

    @Column(name = "segredo_cifrado", nullable = false, length = 255)
    private String segredoCifrado;

    @Column(name = "chave_cifra_id", nullable = false, length = 32)
    private String chaveCifraId;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    @Column(name = "criada_em", nullable = false)
    private Instant criadaEm;

    @Column(name = "revogada_em")
    private Instant revogadaEm;

    public static String contextoCifra(String keyId) {
        return "chave_hmac:" + keyId;
    }

    public boolean utilizavel() {
        return ativa && revogadaEm == null;
    }
}
