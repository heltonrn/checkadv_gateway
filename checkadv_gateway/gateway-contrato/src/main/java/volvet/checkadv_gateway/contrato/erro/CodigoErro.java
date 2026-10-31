package volvet.checkadv_gateway.contrato.erro;

/** Códigos de erro devolvidos no campo {@code codigo} de {@link ErroResposta}. */
public enum CodigoErro {
    /** 400 */ PAYLOAD_INVALIDO,
    /** 401 — corpo mínimo, sem detalhe do motivo. */ NAO_AUTENTICADO,
    /** 403 */ SEM_PERMISSAO,
    /** 404 */ NAO_ENCONTRADO,
    /** 409 */ CONFLITO_IDEMPOTENCIA,
    /** 409 */ ESTADO_INVALIDO,
    /** 422 */ CONTA_SEM_WHATSAPP,
    /** 422 — conta registrada, mas desativada pela instância. */ CONTA_INATIVA,
    /** 422 */ TEMPLATE_INDISPONIVEL,
    /** 422 */ PAGAMENTO_PENDENTE,
    /** 422 */ NUMERO_OPT_OUT,
    /** 429 */ COTA_EXCEDIDA,
    /** 429 */ LIMITE_REQUISICOES,
    /** 500 */ ERRO_INTERNO,
    /** 501 — rota prevista no contrato, ainda sem implementação. */ NAO_IMPLEMENTADO,
    /** 503 */ META_INDISPONIVEL
}
