package volvet.checkadv_gateway.gateway.conta;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.conta.RegistroConta;
import volvet.checkadv_gateway.contrato.conta.SituacaoConta;
import volvet.checkadv_gateway.contrato.conta.StatusWhatsapp;
import volvet.checkadv_gateway.contrato.conta.UsoCota;
import volvet.checkadv_gateway.contrato.erro.CodigoErro;
import volvet.checkadv_gateway.gateway.cota.ServicoCota;
import volvet.checkadv_gateway.gateway.erro.ExcecaoGateway;

/** Registro de contas (escritórios) pelas instâncias e montagem da situação da conta. */
@Service
public class ContaService {

    private final ContaRepository contas;
    private final WhatsappContaConfigRepository whatsapp;
    private final ServicoCota cota;
    private final Clock relogio;

    public ContaService(ContaRepository contas, WhatsappContaConfigRepository whatsapp, ServicoCota cota, Clock relogio) {
        this.contas = contas;
        this.whatsapp = whatsapp;
        this.cota = cota;
        this.relogio = relogio;
    }

    public record Registro(SituacaoConta situacao, boolean criada) {
    }

    /**
     * Idempotente: cria ou atualiza. A conta fica presa ao grupo que a registrou; outra
     * chave de outro grupo recebe 403 (nunca "rouba" a conta).
     */
    @Transactional
    public Registro registrar(String grupoPrefixo, String contaId, RegistroConta registro) {
        Instant agora = relogio.instant();
        Optional<Conta> existente = contas.findById(contaId);
        if (existente.isPresent() && !existente.get().getGrupoPrefixo().equals(grupoPrefixo)) {
            throw new ExcecaoGateway(HttpStatus.FORBIDDEN, CodigoErro.SEM_PERMISSAO,
                    "Conta registrada por outro grupo.");
        }
        Conta conta;
        boolean criada;
        if (existente.isPresent()) {
            conta = existente.get();
            conta.atualizar(registro.nomeExibicao(), registro.ativa(), agora);
            criada = false;
        } else {
            conta = contas.save(Conta.nova(contaId, grupoPrefixo, registro.nomeExibicao(), registro.ativa(), agora));
            criada = true;
        }
        contas.flush();
        return new Registro(montar(conta), criada);
    }

    @Transactional(readOnly = true)
    public SituacaoConta situacao(String grupoPrefixo, String contaId) {
        return montar(buscarDoGrupo(grupoPrefixo, contaId));
    }

    /** Conta de outro grupo é tratada como inexistente: não revela que ela existe. */
    @Transactional(readOnly = true)
    public Conta buscarDoGrupo(String grupoPrefixo, String contaId) {
        return contas.findByContaIdAndGrupoPrefixo(contaId, grupoPrefixo)
                .orElseThrow(() -> ExcecaoGateway.naoEncontrado("Conta não registrada neste grupo."));
    }

    SituacaoConta montar(Conta conta) {
        Optional<WhatsappContaConfig> config = whatsapp.findById(conta.getContaId());
        SituacaoConta.Whatsapp w = config
                .map(c -> new SituacaoConta.Whatsapp(c.getStatus(), c.getNumeroExibicao(), c.getNomeVerificado(),
                        c.getQualidade(), c.getLimiteMensagens()))
                .orElse(new SituacaoConta.Whatsapp(StatusWhatsapp.NAO_CONECTADA, null, null, null, null));
        SituacaoConta.Template t = config
                .filter(c -> c.getTemplateNome() != null)
                .map(c -> new SituacaoConta.Template(c.getTemplateNome(), c.getTemplateIdioma(), c.getTemplateStatus()))
                .orElse(null);
        UsoCota uso = cota.uso(conta.getContaId());
        Integer restantePeriodo = uso.periodo() == null ? null : uso.restante();
        return new SituacaoConta(conta.getContaId(), conta.isAtiva(), w, t,
                new SituacaoConta.Cota(restantePeriodo, uso.diario().restante()));
    }
}
