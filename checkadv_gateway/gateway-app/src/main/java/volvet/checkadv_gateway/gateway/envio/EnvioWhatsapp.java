package volvet.checkadv_gateway.gateway.envio;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import volvet.checkadv_gateway.contrato.envio.CategoriaCobranca;
import volvet.checkadv_gateway.contrato.envio.CategoriaErroEnvio;
import volvet.checkadv_gateway.contrato.envio.StatusEnvio;

/**
 * Linha da fila de envio (outbox). A entidade só é usada para INSERIR e LER; toda mudança
 * de status passa por {@link TransicoesEnvio}, com UPDATE condicional, para que duas
 * réplicas (ou worker e webhook) nunca sobrescrevam uma à outra.
 */
@Entity
@Table(name = "envio_whatsapp")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class EnvioWhatsapp {

    @Id
    @Column(name = "envio_id", length = 36)
    private String envioId;

    @Column(name = "conta_id", nullable = false, length = 64)
    private String contaId;

    @Column(name = "referencia_externa", nullable = false, length = 64)
    private String referenciaExterna;

    @Column(name = "hash_conteudo", nullable = false, length = 64)
    private String hashConteudo;

    @Column(name = "telefone_cifrado", nullable = false, length = 255)
    private String telefoneCifrado;

    @Column(name = "telefone_hash", nullable = false, length = 64)
    private String telefoneHash;

    @Column(name = "telefone_mascarado", nullable = false, length = 32)
    private String telefoneMascarado;

    @Column(name = "template_nome", nullable = false, length = 128)
    private String templateNome;

    @Column(name = "template_idioma", nullable = false, length = 10)
    private String templateIdioma;

    /** Cifrado (contém o token do link); apagado após a retenção. */
    @Column(name = "parametros_json", columnDefinition = "TEXT")
    private String parametrosJson;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private StatusEnvio status;

    @Column(name = "sequencia", nullable = false)
    private int sequencia;

    @Column(name = "tentativas", nullable = false)
    private int tentativas;

    @Column(name = "proxima_tentativa", nullable = false)
    private Instant proximaTentativa;

    @Column(name = "lease_ate")
    private Instant leaseAte;

    @Column(name = "wamid", length = 128)
    private String wamid;

    @Enumerated(EnumType.STRING)
    @Column(name = "categoria_cobranca", length = 20)
    private CategoriaCobranca categoriaCobranca;

    @Column(name = "cobravel")
    private Boolean cobravel;

    @Column(name = "excedente", nullable = false)
    private boolean excedente;

    @Column(name = "cota_dia")
    private LocalDate cotaDia;

    @Column(name = "cota_periodo_inicio")
    private LocalDate cotaPeriodoInicio;

    @Enumerated(EnumType.STRING)
    @Column(name = "erro_categoria", length = 32)
    private CategoriaErroEnvio erroCategoria;

    @Column(name = "erro_mensagem", length = 255)
    private String erroMensagem;

    @Column(name = "erro_codigo_meta", length = 16)
    private String erroCodigoMeta;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    @Column(name = "purgado_em")
    private Instant purgadoEm;

    public static String contextoTelefone(String envioId) {
        return "envio_telefone:" + envioId;
    }

    public static String contextoParametros(String envioId) {
        return "envio_parametros:" + envioId;
    }

    static EnvioWhatsapp novo(String envioId, String contaId, String referenciaExterna, String hashConteudo,
                              String telefoneCifrado, String telefoneHash, String telefoneMascarado,
                              String templateNome, String templateIdioma, String parametrosCifrados,
                              boolean excedente, LocalDate cotaDia, LocalDate cotaPeriodoInicio, Instant agora) {
        var e = new EnvioWhatsapp();
        e.envioId = envioId;
        e.contaId = contaId;
        e.referenciaExterna = referenciaExterna;
        e.hashConteudo = hashConteudo;
        e.telefoneCifrado = telefoneCifrado;
        e.telefoneHash = telefoneHash;
        e.telefoneMascarado = telefoneMascarado;
        e.templateNome = templateNome;
        e.templateIdioma = templateIdioma;
        e.parametrosJson = parametrosCifrados;
        e.status = StatusEnvio.ENFILEIRADO;
        e.sequencia = 0;
        e.tentativas = 0;
        e.proximaTentativa = agora;
        e.excedente = excedente;
        e.cotaDia = cotaDia;
        e.cotaPeriodoInicio = cotaPeriodoInicio;
        e.criadoEm = agora;
        e.atualizadoEm = agora;
        return e;
    }
}
