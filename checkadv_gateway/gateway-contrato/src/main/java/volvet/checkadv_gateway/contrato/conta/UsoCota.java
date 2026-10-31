package volvet.checkadv_gateway.contrato.conta;

import java.time.LocalDate;

/**
 * Corpo de {@code GET /v1/contas/{contaId}/uso}. Sem plano definido, {@code periodo},
 * {@code limite}, {@code usado}, {@code restante} e {@code excedente} vêm nulos (sem limite
 * de período; só o diário vale).
 */
public record UsoCota(
        Periodo periodo,
        Integer limite,
        Integer usado,
        Integer restante,
        Integer excedente,
        Diario diario,
        int unicos24h) {

    public record Periodo(LocalDate inicio, LocalDate fim) {
    }

    public record Diario(int limite, int usado, int restante) {
    }
}
