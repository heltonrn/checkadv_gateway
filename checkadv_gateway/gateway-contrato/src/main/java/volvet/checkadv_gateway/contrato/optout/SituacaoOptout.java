package volvet.checkadv_gateway.contrato.optout;

import java.time.OffsetDateTime;

/** Resposta da consulta de opt-out; {@code registradoEm} e {@code origem} são nulos quando não há bloqueio. */
public record SituacaoOptout(boolean optout, OffsetDateTime registradoEm, OrigemOptout origem) {
}
