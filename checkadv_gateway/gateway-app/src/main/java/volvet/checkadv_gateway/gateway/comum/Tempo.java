package volvet.checkadv_gateway.gateway.comum;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

/**
 * Convenções de data: o banco guarda DATETIME em UTC (sem fuso); a API devolve
 * ISO 8601 com o offset de São Paulo, como no contrato.
 */
public final class Tempo {

    public static final ZoneId FUSO_API = ZoneId.of("America/Sao_Paulo");

    private Tempo() {
    }

    /** Para parâmetros JDBC. */
    public static LocalDateTime utc(Instant instante) {
        return instante == null ? null : LocalDateTime.ofInstant(instante, ZoneOffset.UTC);
    }

    /** De colunas DATETIME lidas via JDBC. */
    public static Instant instante(LocalDateTime utc) {
        return utc == null ? null : utc.toInstant(ZoneOffset.UTC);
    }

    public static OffsetDateTime paraApi(Instant instante) {
        return instante == null ? null : instante.atZone(FUSO_API).toOffsetDateTime();
    }
}
