package volvet.checkadv_gateway.contrato.seguranca;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * Assinatura HMAC-SHA256 usada nas duas direções entre instância e Gateway
 * (requisições da API v1 e callbacks).
 *
 * <pre>
 * texto canônico = timestamp + "\n" + METODO + "\n" + caminhoComQuery + "\n" + hex(SHA256(corpo bruto))
 * X-Assinatura   = "sha256=" + hex(HMAC-SHA256(segredo, texto canônico))
 * </pre>
 *
 * <ul>
 *   <li>{@code caminhoComQuery} é o caminho exatamente como vai na linha de requisição,
 *       já codificado (ex.: {@code /v1/envios?contaId=c-1&referenciaExterna=com-1%231}),
 *       sem esquema nem host. Sem query, não leva {@code ?}.</li>
 *   <li>Corpo vazio (ou nulo) entra como o SHA-256 de zero bytes.</li>
 *   <li>O segredo trafega e é guardado em Base64; a chave HMAC são os bytes decodificados
 *       (mínimo de 32 bytes).</li>
 * </ul>
 *
 * Decisão: o cálculo fica neste módulo sem Spring para que Gateway e instâncias usem
 * exatamente o mesmo código. A classe é imutável e segura para uso concorrente.
 */
public final class AssinadorHmac {

    public static final String ALGORITMO = "HmacSHA256";
    public static final String PREFIXO_ASSINATURA = "sha256=";
    public static final int TAMANHO_MINIMO_SEGREDO = 32;

    private static final HexFormat HEX = HexFormat.of();
    private static final SecureRandom RANDOM = new SecureRandom();

    private final byte[] segredo;

    public AssinadorHmac(byte[] segredo) {
        Objects.requireNonNull(segredo, "segredo");
        if (segredo.length < TAMANHO_MINIMO_SEGREDO) {
            throw new IllegalArgumentException(
                    "Segredo HMAC precisa ter ao menos " + TAMANHO_MINIMO_SEGREDO + " bytes");
        }
        this.segredo = segredo.clone();
    }

    public static AssinadorHmac deSegredoBase64(String segredoBase64) {
        Objects.requireNonNull(segredoBase64, "segredoBase64");
        return new AssinadorHmac(Base64.getDecoder().decode(segredoBase64.trim()));
    }

    /** Gera um segredo novo (32 bytes aleatórios) em Base64. */
    public static String gerarSegredoBase64() {
        byte[] bytes = new byte[TAMANHO_MINIMO_SEGREDO];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    public static String textoCanonico(long timestamp, String metodo, String caminhoComQuery, byte[] corpo) {
        Objects.requireNonNull(metodo, "metodo");
        Objects.requireNonNull(caminhoComQuery, "caminhoComQuery");
        return timestamp + "\n"
                + metodo.toUpperCase(Locale.ROOT) + "\n"
                + caminhoComQuery + "\n"
                + sha256Hex(corpo);
    }

    public String assinar(long timestamp, String metodo, String caminhoComQuery, byte[] corpo) {
        String texto = textoCanonico(timestamp, metodo, caminhoComQuery, corpo);
        return PREFIXO_ASSINATURA + HEX.formatHex(hmac(texto.getBytes(StandardCharsets.UTF_8)));
    }

    /** Monta os três headers de uma requisição ou callback assinado. */
    public CabecalhosAssinados cabecalhos(String keyId, long timestamp, String metodo,
                                          String caminhoComQuery, byte[] corpo) {
        return new CabecalhosAssinados(keyId, timestamp, assinar(timestamp, metodo, caminhoComQuery, corpo));
    }

    /**
     * Confere uma assinatura recebida em tempo constante. Retorna {@code false}
     * (nunca exceção) para valor ausente ou malformado.
     */
    public boolean confere(String assinaturaRecebida, long timestamp, String metodo,
                           String caminhoComQuery, byte[] corpo) {
        if (assinaturaRecebida == null) {
            return false;
        }
        String esperada = assinar(timestamp, metodo, caminhoComQuery, corpo);
        return MessageDigest.isEqual(
                esperada.getBytes(StandardCharsets.US_ASCII),
                assinaturaRecebida.trim().toLowerCase(Locale.ROOT).getBytes(StandardCharsets.US_ASCII));
    }

    public static String sha256Hex(byte[] dados) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HEX.formatHex(digest.digest(dados == null ? new byte[0] : dados));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 indisponível na JVM", e);
        }
    }

    private byte[] hmac(byte[] dados) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(new SecretKeySpec(segredo, ALGORITMO));
            return mac.doFinal(dados);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 indisponível na JVM", e);
        }
    }

    @Override
    public String toString() {
        return "AssinadorHmac[segredo=***]";
    }
}
