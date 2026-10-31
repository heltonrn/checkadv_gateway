package volvet.checkadv_gateway.contrato.callback;

import volvet.checkadv_gateway.contrato.conta.EscopoCota;

/** {@code dados} de {@code cota.alerta}, disparado ao cruzar 80% e 100%. */
public record DadosCotaAlerta(EscopoCota escopo, int percentual, int limite, int usado) {
}
