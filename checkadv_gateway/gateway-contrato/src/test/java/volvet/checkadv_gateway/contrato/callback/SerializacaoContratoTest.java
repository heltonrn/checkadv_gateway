package volvet.checkadv_gateway.contrato.callback;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import tools.jackson.databind.json.JsonMapper;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.contrato.erro.ErroResposta;

/** Garante que o formato JSON do contrato não muda sem querer. */
class SerializacaoContratoTest {

    private final JsonMapper mapper = JsonMapper.builder().build();

    @Test
    void tipoEventoUsaCodigoComPonto() {
        var evento = new EventoCallback<>(
                UUID.fromString("7c1d9e0a-3b2f-4e8c-a1d5-6f0b2e9c4a77"),
                TipoEvento.ENVIO_STATUS_ALTERADO,
                "c-7f3a",
                OffsetDateTime.parse("2026-10-23T08:46:05-03:00"),
                new DadosEnvioStatus(UUID.randomUUID(), "com-1842#1", StatusEnvio.ENTREGUE, 2, null, null));

        String json = mapper.writeValueAsString(evento);

        assertThat(json).contains("\"tipo\":\"envio.status_alterado\"");
        assertThat(mapper.readValue("\"cota.alerta\"", TipoEvento.class)).isEqualTo(TipoEvento.COTA_ALERTA);
    }

    @Test
    void erroSemDetalhesOmiteCampo() {
        String json = mapper.writeValueAsString(ErroResposta.de(CodigoErro.SEM_PERMISSAO, "Sem permissão."));

        assertThat(json).isEqualTo("{\"codigo\":\"SEM_PERMISSAO\",\"mensagem\":\"Sem permissão.\"}");
    }
}
