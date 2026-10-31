package volvet.checkadv_gateway.contrato.envio;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Corpo de {@code GET /v1/envios/{envioId}} e {@code GET /v1/envios?contaId=&referenciaExterna=}.
 * O wamid e o telefone completo nunca saem do Gateway.
 */
public record EnvioDetalhe(
        UUID envioId,
        String contaId,
        String referenciaExterna,
        StatusEnvio status,
        long sequencia,
        DestinatarioMascarado destinatario,
        TemplateResumo template,
        Cobranca cobranca,
        ErroEnvio erro,
        List<HistoricoStatus> historico,
        OffsetDateTime criadoEm,
        OffsetDateTime atualizadoEm) {

    public record DestinatarioMascarado(String telefoneMascarado) {
    }

    public record TemplateResumo(String nome, String idioma) {
    }
}
