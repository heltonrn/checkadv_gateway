package volvet.checkadv_gateway.gateway.cripto;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class CifradorAesGcmTest {

    private final CifradorAesGcm cifrador = new CifradorAesGcm("k1", CifradorAesGcm.gerarChaveBase64());

    @Test
    void idaEVolta() {
        String cifrado = cifrador.cifrarTexto("token-da-meta", "whatsapp_token:c-1");

        assertThat(cifrado).doesNotContain("token-da-meta");
        assertThat(cifrador.decifrarTexto(cifrado, "k1", "whatsapp_token:c-1")).isEqualTo("token-da-meta");
    }

    @Test
    void ivAleatorioGeraCifrasDiferentes() {
        assertThat(cifrador.cifrarTexto("x", "c")).isNotEqualTo(cifrador.cifrarTexto("x", "c"));
    }

    @Test
    void contextoDiferenteNaoDecifra() {
        String cifrado = cifrador.cifrarTexto("segredo", "chave_hmac:grp-A");

        assertThatThrownBy(() -> cifrador.decifrar(cifrado, "k1", "chave_hmac:grp-B"))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void outraChaveMestraNaoDecifra() {
        String cifrado = cifrador.cifrarTexto("segredo", "c");
        var outro = new CifradorAesGcm("k1", CifradorAesGcm.gerarChaveBase64());

        assertThatThrownBy(() -> outro.decifrar(cifrado, "k1", "c")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> cifrador.decifrar(cifrado, "k2", "c")).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void chaveDeTamanhoErradoEhRecusada() {
        assertThatThrownBy(() -> new CifradorAesGcm("k1", "AAECAwQFBgcICQoLDA0ODw=="))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
