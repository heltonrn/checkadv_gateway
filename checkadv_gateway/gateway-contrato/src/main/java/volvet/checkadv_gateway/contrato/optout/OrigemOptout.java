package volvet.checkadv_gateway.contrato.optout;

public enum OrigemOptout {
    /** Cliente respondeu com palavra de descadastro (ex.: SAIR, PARAR). */
    PALAVRA_CHAVE,
    /** Escritório registrou o pedido do cliente por outro canal. */
    SOLICITACAO_ESCRITORIO
}
