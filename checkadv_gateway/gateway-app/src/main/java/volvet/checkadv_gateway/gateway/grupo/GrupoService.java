package volvet.checkadv_gateway.gateway.grupo;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.callback.SituacaoCallback;
import volvet.checkadv_gateway.contrato.callback.TipoEvento;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.gateway.callback.PublicadorEventos;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.config.CallbackProperties;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;

/** Callback do grupo: URL de entrega dos eventos à instância. */
@Service
public class GrupoService {

    private final GrupoInstanciaRepository grupos;
    private final PublicadorEventos eventos;
    private final CallbackProperties props;
    private final JdbcClient jdbc;
    private final Clock relogio;

    public GrupoService(GrupoInstanciaRepository grupos, PublicadorEventos eventos, CallbackProperties props,
                        JdbcClient jdbc, Clock relogio) {
        this.grupos = grupos;
        this.eventos = eventos;
        this.props = props;
        this.jdbc = jdbc;
        this.relogio = relogio;
    }

    @Transactional(readOnly = true)
    public SituacaoCallback situacaoCallback(String grupoPrefixo) {
        GrupoInstancia g = buscar(grupoPrefixo);
        LocalDateTime ultima = jdbc.sql("SELECT MAX(entregue_em) FROM callback_pendente WHERE grupo_prefixo = ?")
                .param(grupoPrefixo).query(LocalDateTime.class).optional().orElse(null);
        return new SituacaoCallback(g.getCallbackUrl(), g.isAtivo() && g.getCallbackUrl() != null,
                Tempo.paraApi(Tempo.instante(ultima)));
    }

    /**
     * Anti-SSRF: só HTTPS (HTTP apenas se liberado para desenvolvimento) e host na lista
     * cadastrada pelo operador. Assim uma instância não consegue fazer o Gateway chamar
     * um endereço interno qualquer.
     */
    @Transactional
    public SituacaoCallback registrarCallback(String grupoPrefixo, String url) {
        GrupoInstancia g = buscar(grupoPrefixo);
        URI uri;
        try {
            uri = URI.create(url);
        } catch (IllegalArgumentException e) {
            throw invalida("URL inválida.");
        }
        String esquema = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!esquema.equals("https") && !(props.permitirHttp() && esquema.equals("http"))) {
            throw invalida("O callback precisa usar HTTPS.");
        }
        String host = uri.getHost() == null ? "" : uri.getHost().toLowerCase(Locale.ROOT);
        if (uri.getUserInfo() != null || uri.getFragment() != null
                || g.hostsPermitidos().stream().noneMatch(h -> h.equalsIgnoreCase(host))) {
            throw invalida("Host não permitido para este grupo.");
        }
        g.definirCallback(url, relogio.instant());
        grupos.saveAndFlush(g);
        return situacaoCallback(grupoPrefixo);
    }

    @Transactional
    public void dispararTeste(String grupoPrefixo) {
        GrupoInstancia g = buscar(grupoPrefixo);
        if (g.getCallbackUrl() == null) {
            throw new ExcecaoGateway(HttpStatus.CONFLICT, CodigoErro.ESTADO_INVALIDO, "Nenhuma URL de callback registrada.");
        }
        eventos.publicar(grupoPrefixo, null, TipoEvento.TESTE_PING,
                Map.of("mensagem", "Teste de callback do CheckADV Gateway"), null, null);
    }

    private GrupoInstancia buscar(String grupoPrefixo) {
        return grupos.findById(grupoPrefixo).orElseThrow(() -> ExcecaoGateway.naoEncontrado("Grupo não encontrado."));
    }

    private static ExcecaoGateway invalida(String mensagem) {
        return new ExcecaoGateway(HttpStatus.BAD_REQUEST, CodigoErro.PAYLOAD_INVALIDO, mensagem,
                Map.of("campos", Map.of("url", mensagem)));
    }
}
