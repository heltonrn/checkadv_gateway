package volvet.checkadv_gateway.gateway.grupo;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/** Uma instância do CheckADV (grupo de escritórios com banco próprio). */
@Entity
@Table(name = "grupo_instancia")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GrupoInstancia {

    @Id
    @Column(name = "prefixo", length = 4)
    private String prefixo;

    @Column(name = "nome", nullable = false, length = 100)
    private String nome;

    @Column(name = "base_url_interna", nullable = false, length = 255)
    private String baseUrlInterna;

    @Column(name = "callback_url", length = 500)
    private String callbackUrl;

    /** Lista separada por vírgula. */
    @Column(name = "hosts_callback_permitidos", length = 1000)
    private String hostsCallbackPermitidos;

    @Column(name = "ativo", nullable = false)
    private boolean ativo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public static GrupoInstancia novo(String prefixo, String nome, String baseUrlInterna,
                                      List<String> hostsCallback, Instant agora) {
        var g = new GrupoInstancia();
        g.prefixo = prefixo;
        g.nome = nome;
        g.baseUrlInterna = baseUrlInterna;
        g.hostsCallbackPermitidos = String.join(",", hostsCallback);
        g.ativo = true;
        g.criadoEm = agora;
        g.atualizadoEm = agora;
        return g;
    }

    public List<String> hostsPermitidos() {
        if (hostsCallbackPermitidos == null || hostsCallbackPermitidos.isBlank()) {
            return List.of();
        }
        return Arrays.stream(hostsCallbackPermitidos.split(",")).map(String::trim).filter(h -> !h.isEmpty()).toList();
    }

    public void definirCallback(String url, Instant agora) {
        this.callbackUrl = url;
        this.atualizadoEm = agora;
    }

    public void alterar(Boolean ativo, String baseUrlInterna, Instant agora) {
        if (ativo != null) {
            this.ativo = ativo;
        }
        if (baseUrlInterna != null) {
            this.baseUrlInterna = baseUrlInterna;
        }
        this.atualizadoEm = agora;
    }
}
