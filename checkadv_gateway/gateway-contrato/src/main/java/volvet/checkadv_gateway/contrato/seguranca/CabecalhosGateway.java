package volvet.checkadv_gateway.contrato.seguranca;

/** Nomes de headers e limites comuns da API v1 e dos callbacks. */
public final class CabecalhosGateway {

    public static final String KEY_ID = "X-Key-Id";
    public static final String TIMESTAMP = "X-Timestamp";
    public static final String ASSINATURA = "X-Assinatura";
    public static final String CORRELATION_ID = "X-Correlation-Id";
    public static final String COTA_RESTANTE = "X-Cota-Restante";

    /** Tolerância do X-Timestamp, em segundos, para mais ou para menos. */
    public static final long JANELA_TIMESTAMP_SEGUNDOS = 300;

    /** Tamanho máximo do corpo das requisições da v1. */
    public static final int CORPO_MAXIMO_BYTES = 8 * 1024;

    private CabecalhosGateway() {
    }
}
