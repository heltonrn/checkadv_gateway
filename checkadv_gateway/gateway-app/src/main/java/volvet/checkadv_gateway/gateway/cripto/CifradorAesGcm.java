package volvet.checkadv_gateway.gateway.cripto;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Objects;

import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * AES-256-GCM para segredos guardados no banco (segredos HMAC, tokens da Meta, telefones).
 *
 * Formato gravado: Base64( IV(12) || texto cifrado || tag(16) ). O {@code contexto} entra
 * como AAD (ex.: {@code chave_hmac:<keyId>}), então um valor copiado para outra linha
 * não decifra. O id da chave mestra é gravado em coluna própria para permitir rotação.
 */
public final class CifradorAesGcm {

    private static final String TRANSFORMACAO = "AES/GCM/NoPadding";
    private static final int TAMANHO_IV = 12;
    private static final int TAG_BITS = 128;
    private static final int TAMANHO_CHAVE = 32;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final String chaveId;
    private final SecretKey chave;

    public CifradorAesGcm(String chaveId, String chaveBase64) {
        this.chaveId = Objects.requireNonNull(chaveId, "chaveId");
        byte[] bytes = Base64.getDecoder().decode(Objects.requireNonNull(chaveBase64, "chaveBase64").trim());
        if (bytes.length != TAMANHO_CHAVE) {
            throw new IllegalArgumentException("Chave mestra precisa ter 32 bytes (AES-256)");
        }
        this.chave = new SecretKeySpec(bytes, "AES");
    }

    public static String gerarChaveBase64() {
        byte[] bytes = new byte[TAMANHO_CHAVE];
        RANDOM.nextBytes(bytes);
        return Base64.getEncoder().encodeToString(bytes);
    }

    /**
     * Deriva uma chave de uso específico a partir da chave mestra (HMAC-SHA256 com um rótulo
     * fixo). Evita uma variável de ambiente por finalidade; trocar o rótulo gera outra chave.
     */
    public byte[] derivarChave(String rotulo) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chave.getEncoded(), "HmacSHA256"));
            return mac.doFinal(rotulo.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao derivar chave", e);
        }
    }

    public String chaveId() {
        return chaveId;
    }

    public String cifrar(byte[] plano, String contexto) {
        try {
            byte[] iv = new byte[TAMANHO_IV];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.ENCRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, iv));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            byte[] cifrado = cipher.doFinal(plano);
            return Base64.getEncoder().encodeToString(
                    ByteBuffer.allocate(iv.length + cifrado.length).put(iv).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao cifrar", e);
        }
    }

    public String cifrarTexto(String plano, String contexto) {
        return cifrar(plano.getBytes(StandardCharsets.UTF_8), contexto);
    }

    /**
     * @throws IllegalStateException se o valor foi cifrado com outra chave mestra,
     *                               adulterado ou gravado em outro contexto
     */
    public byte[] decifrar(String valorBase64, String chaveIdGravada, String contexto) {
        if (!chaveId.equals(chaveIdGravada)) {
            throw new IllegalStateException("Valor cifrado com chave mestra '" + chaveIdGravada
                    + "', mas a chave carregada é '" + chaveId + "'");
        }
        try {
            byte[] dados = Base64.getDecoder().decode(valorBase64);
            Cipher cipher = Cipher.getInstance(TRANSFORMACAO);
            cipher.init(Cipher.DECRYPT_MODE, chave, new GCMParameterSpec(TAG_BITS, dados, 0, TAMANHO_IV));
            cipher.updateAAD(contexto.getBytes(StandardCharsets.UTF_8));
            return cipher.doFinal(dados, TAMANHO_IV, dados.length - TAMANHO_IV);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("Falha ao decifrar (chave, contexto ou valor inválido)", e);
        }
    }

    public String decifrarTexto(String valorBase64, String chaveIdGravada, String contexto) {
        return new String(decifrar(valorBase64, chaveIdGravada, contexto), StandardCharsets.UTF_8);
    }

    @Override
    public String toString() {
        return "CifradorAesGcm[chaveId=" + chaveId + ", chave=***]";
    }
}
