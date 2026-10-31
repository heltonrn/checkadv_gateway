package volvet.checkadv_gateway.gateway.cripto;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.stereotype.Component;

/**
 * Tratamento dos dados pessoais guardados pelo Gateway (LGPD: só o necessário).
 *
 * <ul>
 *   <li>Telefone cifrado (AES-GCM): o worker decifra na hora de enviar.</li>
 *   <li>Telefone em hash (HMAC com chave derivada da mestra): contar destinatários
 *       únicos e consultar opt-out sem decifrar. Sem a chave, o hash não pode ser
 *       revertido por força bruta sobre os números possíveis.</li>
 *   <li>Telefone mascarado: o único formato que sai em respostas e logs.</li>
 * </ul>
 */
@Component
public class ProtecaoDados {

    private static final String ROTULO_HASH_TELEFONE = "checkadv:telefone-hash:v1";
    private static final HexFormat HEX = HexFormat.of();

    private final CifradorAesGcm cifrador;
    private final byte[] chaveHashTelefone;

    public ProtecaoDados(CifradorAesGcm cifrador) {
        this.cifrador = cifrador;
        this.chaveHashTelefone = cifrador.derivarChave(ROTULO_HASH_TELEFONE);
    }

    public String cifrar(String texto, String contexto) {
        return cifrador.cifrarTexto(texto, contexto);
    }

    public String decifrar(String cifrado, String chaveCifraId, String contexto) {
        return cifrador.decifrarTexto(cifrado, chaveCifraId, contexto);
    }

    public String chaveCifraId() {
        return cifrador.chaveId();
    }

    public String hashTelefone(String e164) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(chaveHashTelefone, "HmacSHA256"));
            return HEX.formatHex(mac.doFinal(e164.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("Falha ao calcular hash do telefone", e);
        }
    }

    /**
     * {@code +5544999998888 → +55 44 9****-8888}; {@code +554433330000 → +55 44 ****-0000};
     * outros países: {@code +1 ******1934}.
     */
    public static String mascararTelefone(String e164) {
        String digitos = e164.startsWith("+") ? e164.substring(1) : e164;
        if (digitos.startsWith("55") && (digitos.length() == 13 || digitos.length() == 12)) {
            String ddd = digitos.substring(2, 4);
            String final4 = digitos.substring(digitos.length() - 4);
            return digitos.length() == 13
                    ? "+55 " + ddd + " " + digitos.charAt(4) + "****-" + final4
                    : "+55 " + ddd + " ****-" + final4;
        }
        if (digitos.length() <= 6) {
            return "+" + "*".repeat(digitos.length());
        }
        String pais = digitos.substring(0, 1);
        return "+" + pais + " " + "*".repeat(digitos.length() - 5) + digitos.substring(digitos.length() - 4);
    }
}
