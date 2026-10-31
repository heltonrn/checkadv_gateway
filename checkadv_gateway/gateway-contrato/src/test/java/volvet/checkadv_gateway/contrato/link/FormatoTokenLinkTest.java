package volvet.checkadv_gateway.contrato.link;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FormatoTokenLinkTest {

    @Test
    void tokenValidoDevolvePrefixo() {
        assertThat(FormatoTokenLink.prefixoDe("A1B2q8Zr0kT3vYw9LmNpXc4dEe")).contains("A1B2");
        assertThat(FormatoTokenLink.prefixoDe("a-_Zq8Zr0kT3vYw9LmNpXc4dEe")).contains("a-_Z");
    }

    @Test
    void formatosInvalidos() {
        assertThat(FormatoTokenLink.tokenValido(null)).isFalse();
        assertThat(FormatoTokenLink.tokenValido("A1B2q8Zr0kT3vYw9LmNpXc4dE")).isFalse();   // 25
        assertThat(FormatoTokenLink.tokenValido("A1B2q8Zr0kT3vYw9LmNpXc4dEee")).isFalse(); // 27
        assertThat(FormatoTokenLink.tokenValido("A1B2q8Zr0kT3vYw9LmNpXc4d+=")).isFalse();  // não URL-safe
        assertThat(FormatoTokenLink.tokenValido("A1B2q8Zr0kT3vYw9LmNpXc4d/.")).isFalse();
        assertThat(FormatoTokenLink.prefixoDe("curto")).isEmpty();
    }

    @Test
    void prefixo() {
        assertThat(FormatoTokenLink.prefixoValido("A1B2")).isTrue();
        assertThat(FormatoTokenLink.prefixoValido("A1B")).isFalse();
        assertThat(FormatoTokenLink.prefixoValido("A1B=")).isFalse();
    }
}
