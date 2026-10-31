package volvet.checkadv_gateway.contrato.seguranca;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.Test;

/**
 * Vetores calculados de forma independente (Python: hmac + hashlib) com o segredo
 * = bytes 0..31. Qualquer implementação do lado da instância deve chegar nos mesmos valores.
 */
class AssinadorHmacTest {

    private static final String SEGREDO_B64 = "AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8=";
    private static final long TS = 1792760000L;

    private final AssinadorHmac assinador = AssinadorHmac.deSegredoBase64(SEGREDO_B64);

    @Test
    void postComCorpo() {
        byte[] corpo = "{\"contaId\":\"c-7f3a\"}".getBytes(StandardCharsets.UTF_8);

        assertThat(AssinadorHmac.textoCanonico(TS, "POST", "/v1/envios", corpo)).isEqualTo(
                "1792760000\nPOST\n/v1/envios\n7fa2775004bbf623843ab0c331226351d17f2e9cc84e914845d88f52ba5b4a8e");
        assertThat(assinador.assinar(TS, "POST", "/v1/envios", corpo))
                .isEqualTo("sha256=6f4c9890474efd0a0a92dbfbb03c4f1cdc04904b6699b484831afa3e654af070");
    }

    @Test
    void getComQueryCodificadaECorpoVazio() {
        String caminho = "/v1/envios?contaId=c-7f3a&referenciaExterna=com-1842%231";

        assertThat(assinador.assinar(TS, "get", caminho, null))
                .isEqualTo("sha256=0e1d95b471e2af9b08eaeefda500df5a86b80362a987ac3d8e1486cab09cff00");
        assertThat(assinador.assinar(TS, "GET", caminho, new byte[0]))
                .isEqualTo(assinador.assinar(TS, "GET", caminho, null));
    }

    @Test
    void confereAceitaAssinaturaCorretaEmMaiusculas() {
        String assinatura = assinador.assinar(TS, "POST", "/v1/envios", new byte[0]);

        assertThat(assinador.confere(assinatura, TS, "POST", "/v1/envios", new byte[0])).isTrue();
        assertThat(assinador.confere(assinatura.toUpperCase().replace("SHA256=", "sha256="),
                TS, "POST", "/v1/envios", new byte[0])).isTrue();
    }

    @Test
    void confereRejeitaQualquerAlteracao() {
        byte[] corpo = "{}".getBytes(StandardCharsets.UTF_8);
        String assinatura = assinador.assinar(TS, "POST", "/v1/envios", corpo);

        assertThat(assinador.confere(assinatura, TS + 1, "POST", "/v1/envios", corpo)).isFalse();
        assertThat(assinador.confere(assinatura, TS, "PUT", "/v1/envios", corpo)).isFalse();
        assertThat(assinador.confere(assinatura, TS, "POST", "/v1/envios?x=1", corpo)).isFalse();
        assertThat(assinador.confere(assinatura, TS, "POST", "/v1/envios", "{ }".getBytes())).isFalse();
        assertThat(assinador.confere(null, TS, "POST", "/v1/envios", corpo)).isFalse();
        assertThat(assinador.confere("lixo", TS, "POST", "/v1/envios", corpo)).isFalse();
    }

    @Test
    void outroSegredoNaoConfere() {
        AssinadorHmac outro = AssinadorHmac.deSegredoBase64(AssinadorHmac.gerarSegredoBase64());
        String assinatura = outro.assinar(TS, "POST", "/v1/envios", null);

        assertThat(assinador.confere(assinatura, TS, "POST", "/v1/envios", null)).isFalse();
    }

    @Test
    void segredoCurtoEhRecusado() {
        assertThatThrownBy(() -> new AssinadorHmac(new byte[16]))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void segredoGeradoTem32Bytes() {
        assertThat(Base64.getDecoder().decode(AssinadorHmac.gerarSegredoBase64())).hasSize(32);
    }

    @Test
    void toStringNaoExpoeSegredo() {
        assertThat(assinador.toString()).doesNotContain(SEGREDO_B64);
    }
}
