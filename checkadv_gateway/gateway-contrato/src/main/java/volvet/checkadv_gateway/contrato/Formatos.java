package volvet.checkadv_gateway.contrato;

/** Expressões regulares compartilhadas pelos DTOs e pelos parâmetros de rota. */
public final class Formatos {

    /** contaId e outros identificadores opacos vindos da instância. */
    public static final String ID_OPACO = "^[A-Za-z0-9._-]{1,64}$";

    /** Chave de idempotência do envio; '#' permite o sufixo de reenvio (com-1842#2). */
    public static final String REFERENCIA_EXTERNA = "^[A-Za-z0-9#._-]{1,64}$";

    /** Telefone E.164: '+', código do país, até 15 dígitos no total. */
    public static final String TELEFONE_E164 = "^\\+[1-9][0-9]{7,14}$";

    private Formatos() {
    }
}
