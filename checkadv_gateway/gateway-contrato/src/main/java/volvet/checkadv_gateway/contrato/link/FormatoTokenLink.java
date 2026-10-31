package volvet.checkadv_gateway.contrato.link;

import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Formato do token do link público: {@code <prefixo do grupo (4)><aleatório (22)>},
 * 26 caracteres Base64URL sem padding. O prefixo identifica o GRUPO de instâncias
 * (não o servidor) e não é segredo; os 22 caracteres finais são 16 bytes aleatórios.
 *
 * Usado pelo Resolvedor (roteamento), pelo Gateway (validação do botaoUrl) e pela
 * instância (rejeitar tokens de outro grupo).
 */
public final class FormatoTokenLink {

    public static final int TAMANHO_TOKEN = 26;
    public static final int TAMANHO_PREFIXO = 4;
    public static final String REGEX_TOKEN = "^[A-Za-z0-9_-]{26}$";
    public static final String REGEX_PREFIXO = "^[A-Za-z0-9_-]{4}$";

    private static final Pattern TOKEN = Pattern.compile(REGEX_TOKEN);
    private static final Pattern PREFIXO = Pattern.compile(REGEX_PREFIXO);

    private FormatoTokenLink() {
    }

    public static boolean tokenValido(String token) {
        return token != null && TOKEN.matcher(token).matches();
    }

    public static boolean prefixoValido(String prefixo) {
        return prefixo != null && PREFIXO.matcher(prefixo).matches();
    }

    /** Prefixo do grupo, ou vazio se o token não tiver o formato esperado. */
    public static Optional<String> prefixoDe(String token) {
        return tokenValido(token) ? Optional.of(token.substring(0, TAMANHO_PREFIXO)) : Optional.empty();
    }
}
