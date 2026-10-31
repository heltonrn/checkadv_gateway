package volvet.checkadv_gateway.gateway.admin;

/** Resposta 201 de {@code POST /admin/grupos}, já com a primeira chave do grupo. */
public record GrupoCriado(String prefixo, boolean ativo, ChaveGerada chave) {
}
