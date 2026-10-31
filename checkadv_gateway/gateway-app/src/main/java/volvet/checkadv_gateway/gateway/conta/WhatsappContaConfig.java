package volvet.checkadv_gateway.gateway.conta;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import volvet.checkadv_gateway.contrato.conta.StatusTemplate;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;

/**
 * Número WhatsApp do escritório e template na WABA dele. A ausência da linha equivale a
 * NAO_CONECTADA. O token da Meta fica cifrado (AAD {@code whatsapp_token:<contaId>}).
 */
@Entity
@Table(name = "whatsapp_conta_config")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WhatsappContaConfig {

    @Id
    @Column(name = "conta_id", length = 64)
    private String contaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private StatusWhatsapp status;

    @Column(name = "waba_id", length = 32)
    private String wabaId;

    @Column(name = "phone_number_id", length = 32)
    private String phoneNumberId;

    @Column(name = "numero_exibicao", length = 32)
    private String numeroExibicao;

    @Column(name = "nome_verificado", length = 120)
    private String nomeVerificado;

    @Column(name = "qualidade", length = 16)
    private String qualidade;

    @Column(name = "limite_mensagens", length = 32)
    private String limiteMensagens;

    @Column(name = "token_cifrado", length = 2048)
    private String tokenCifrado;

    @Column(name = "chave_cifra_id", length = 32)
    private String chaveCifraId;

    @Column(name = "template_nome", length = 128)
    private String templateNome;

    @Column(name = "template_idioma", length = 10)
    private String templateIdioma;

    @Enumerated(EnumType.STRING)
    @Column(name = "template_status", length = 16)
    private StatusTemplate templateStatus;

    @Column(name = "template_motivo", length = 255)
    private String templateMotivo;

    @Column(name = "criado_em", nullable = false)
    private Instant criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private Instant atualizadoEm;

    public static String contextoToken(String contaId) {
        return "whatsapp_token:" + contaId;
    }

    public static WhatsappContaConfig nova(String contaId, Instant agora) {
        var c = new WhatsappContaConfig();
        c.contaId = contaId;
        c.status = StatusWhatsapp.NAO_CONECTADA;
        c.criadoEm = agora;
        c.atualizadoEm = agora;
        return c;
    }

    public void iniciarConexao(String wabaId, String phoneNumberId, Instant agora) {
        this.status = StatusWhatsapp.CONECTANDO;
        this.wabaId = wabaId;
        this.phoneNumberId = phoneNumberId;
        this.atualizadoEm = agora;
    }

    public void conectar(String tokenCifrado, String chaveCifraId, String numeroExibicao, String nomeVerificado,
                         String qualidade, String limiteMensagens, Instant agora) {
        this.status = StatusWhatsapp.CONECTADA;
        this.tokenCifrado = tokenCifrado;
        this.chaveCifraId = chaveCifraId;
        this.numeroExibicao = numeroExibicao;
        this.nomeVerificado = nomeVerificado;
        this.qualidade = qualidade;
        this.limiteMensagens = limiteMensagens;
        this.atualizadoEm = agora;
    }

    public void falharConexao(Instant agora) {
        this.status = StatusWhatsapp.NAO_CONECTADA;
        this.tokenCifrado = null;
        this.chaveCifraId = null;
        this.atualizadoEm = agora;
    }

    /** Apaga o token: sem ele, nada mais sai em nome do escritório. */
    public void desconectar(StatusWhatsapp novoStatus, Instant agora) {
        this.status = novoStatus;
        this.tokenCifrado = null;
        this.chaveCifraId = null;
        this.atualizadoEm = agora;
    }

    public void atualizarTemplate(String nome, String idioma, StatusTemplate status, String motivo, Instant agora) {
        this.templateNome = nome;
        this.templateIdioma = idioma;
        this.templateStatus = status;
        this.templateMotivo = motivo;
        this.atualizadoEm = agora;
    }

    public void atualizarQualidade(String qualidade, String limiteMensagens, Instant agora) {
        this.qualidade = qualidade;
        this.limiteMensagens = limiteMensagens;
        this.atualizadoEm = agora;
    }

    public boolean prontaParaEnviar(String nomeTemplate, String idioma) {
        return status == StatusWhatsapp.CONECTADA
                && templateStatus == StatusTemplate.APROVADO
                && nomeTemplate.equals(templateNome)
                && idioma.equals(templateIdioma);
    }
}
