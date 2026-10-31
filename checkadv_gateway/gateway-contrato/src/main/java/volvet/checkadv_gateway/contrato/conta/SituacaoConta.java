package volvet.checkadv_gateway.contrato.conta;

/** Corpo de {@code GET /v1/contas/{contaId}}. */
public record SituacaoConta(
        String contaId,
        boolean ativa,
        Whatsapp whatsapp,
        Template template,
        Cota cota) {

    /**
     * @param qualidade       GREEN, YELLOW, RED ou UNKNOWN, como a Meta informa
     * @param limiteMensagens tier de limite da Meta (ex.: TIER_250)
     */
    public record Whatsapp(StatusWhatsapp status, String numeroExibicao, String nomeVerificado,
                           String qualidade, String limiteMensagens) {
    }

    public record Template(String nome, String idioma, StatusTemplate status) {
    }

    public record Cota(Integer restantePeriodo, Integer restanteDia) {
    }
}
