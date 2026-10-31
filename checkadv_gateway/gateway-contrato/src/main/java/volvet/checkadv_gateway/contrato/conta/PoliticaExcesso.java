package volvet.checkadv_gateway.contrato.conta;

/** O que fazer quando a cota do período acaba. Padrão do sistema ainda em aberto. */
public enum PoliticaExcesso {
    BLOQUEAR,
    PERMITIR_COBRAR_EXTRA
}
