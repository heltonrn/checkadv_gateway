package volvet.checkadv_gateway.gateway.cota;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import volvet.checkadv_gateway.contrato.conta.PoliticaExcesso;

/** Plano vigente da conta, definido pelo CheckADV. Sem linha: só o limite diário padrão. */
@Entity
@Table(name = "cota_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CotaConfig {

    @Id
    @Column(name = "conta_id", length = 64)
    private String contaId;

    @Column(name = "periodo_inicio", nullable = false)
    private LocalDate periodoInicio;

    @Column(name = "periodo_fim", nullable = false)
    private LocalDate periodoFim;

    @Column(name = "limite", nullable = false)
    private int limite;

    @Column(name = "limite_diario", nullable = false)
    private int limiteDiario;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_excesso", nullable = false, length = 24)
    private PoliticaExcesso politicaExcesso;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public static CotaConfig nova(String contaId) {
        var c = new CotaConfig();
        c.contaId = contaId;
        return c;
    }

    public void definir(LocalDate inicio, LocalDate fim, int limite, int limiteDiario,
                        PoliticaExcesso politica, Instant agora) {
        this.periodoInicio = inicio;
        this.periodoFim = fim;
        this.limite = limite;
        this.limiteDiario = limiteDiario;
        this.politicaExcesso = politica;
        this.atualizadoEm = agora;
    }

    public boolean vigenteEm(LocalDate dia) {
        return !dia.isBefore(periodoInicio) && !dia.isAfter(periodoFim);
    }
}
