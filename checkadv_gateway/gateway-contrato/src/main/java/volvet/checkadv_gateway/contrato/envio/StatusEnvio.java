package volvet.checkadv_gateway.contrato.envio;

/**
 * Ciclo de vida de um envio.
 *
 * <pre>
 * ENFILEIRADO → ENVIANDO → ENVIADO → ENTREGUE → LIDO
 *                  ↘ FALHOU | INCERTO          CANCELADO (só a partir de ENFILEIRADO)
 * </pre>
 *
 * ENVIANDO é interno: não incrementa {@code sequencia} nem gera callback.
 */
public enum StatusEnvio {
    ENFILEIRADO(false, false),
    ENVIANDO(false, false),
    ENVIADO(true, false),
    ENTREGUE(true, false),
    LIDO(true, true),
    FALHOU(true, true),
    INCERTO(true, true),
    CANCELADO(true, true);

    private final boolean notificavel;
    private final boolean terminal;

    StatusEnvio(boolean notificavel, boolean terminal) {
        this.notificavel = notificavel;
        this.terminal = terminal;
    }

    /** Transições para este status incrementam {@code sequencia} e geram callback. */
    public boolean notificavel() {
        return notificavel;
    }

    /**
     * Não há transição posterior iniciada pelo Gateway. ENTREGUE ainda pode virar LIDO,
     * por isso não é terminal.
     */
    public boolean terminal() {
        return terminal;
    }
}
