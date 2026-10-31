package volvet.checkadv_gateway.contrato.conta;

import java.time.LocalDate;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Corpo de {@code PUT /v1/contas/{contaId}/cota}. Datas no fuso America/Sao_Paulo,
 * fim inclusivo (permite ciclo pelo aniversário do contrato).
 */
public record DefinicaoCota(
        @NotNull LocalDate periodoInicio,
        @NotNull LocalDate periodoFim,
        @NotNull @Min(0) Integer limite,
        @NotNull @Min(0) Integer limiteDiario,
        @NotNull PoliticaExcesso politicaExcesso) {
}
