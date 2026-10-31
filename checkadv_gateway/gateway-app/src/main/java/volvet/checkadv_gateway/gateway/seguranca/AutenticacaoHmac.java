package volvet.checkadv_gateway.gateway.seguranca;

import java.util.List;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/** Autenticação já validada pelo {@link FiltroAutenticacaoHmac}; papel ROLE_GRUPO ou ROLE_OPERADOR. */
public class AutenticacaoHmac extends AbstractAuthenticationToken {

    private final ChaveAutenticada chave;

    public AutenticacaoHmac(ChaveAutenticada chave) {
        super(List.of(new SimpleGrantedAuthority("ROLE_" + chave.tipo().name())));
        this.chave = chave;
        setAuthenticated(true);
    }

    @Override
    public ChaveAutenticada getPrincipal() {
        return chave;
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public String getName() {
        return chave.keyId();
    }
}
