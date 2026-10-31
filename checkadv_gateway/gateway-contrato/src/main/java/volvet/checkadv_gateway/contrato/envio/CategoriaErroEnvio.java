package volvet.checkadv_gateway.contrato.envio;

/** Categoria de falha de entrega informada no callback e no GET do envio. */
public enum CategoriaErroEnvio {
    NUMERO_SEM_WHATSAPP,
    TEMPLATE_NAO_APROVADO,
    PAGAMENTO_PENDENTE,
    LIMITE_META_EXCEDIDO,
    CONTA_DESCONECTADA,
    ERRO_META,
    ERRO_INTERNO,
    /** Timeout ou 5xx depois de conectar: a Meta pode ou não ter enviado. */
    ENVIO_INCERTO
}
