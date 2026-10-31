package volvet.checkadv_gateway.gateway.conta;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Escritório contratante, registrado pela instância do grupo. O {@code contaId} é opaco
 * para o Gateway e precisa ser único entre todos os grupos (recomendação: UUID da Conta).
 */
@Entity
@Table(name = "conta")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Conta {

    @Id
    @Column(name = "conta_id", length = 64)
    private String contaId;

    @Column(name = "grupo_prefixo", nullable = false, length = 4)
    private String grupoPrefixo;

    @Column(name = "nome_exibicao", nullable = false, length = 120)
    private String nomeExibicao;

    @Column(name = "ativa", nullable = false)
    private boolean ativa;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public static Conta nova(String contaId, String grupoPrefixo, String nomeExibicao, boolean ativa, Instant agora) {
        var c = new Conta();
        c.contaId = contaId;
        c.grupoPrefixo = grupoPrefixo;
        c.nomeExibicao = nomeExibicao;
        c.ativa = ativa;
        c.criadoEm = agora;
        c.atualizadoEm = agora;
        return c;
    }

    public void atualizar(String nomeExibicao, boolean ativa, Instant agora) {
        this.nomeExibicao = nomeExibicao;
        this.ativa = ativa;
        this.atualizadoEm = agora;
    }
}
