package volvet.checkadv_gateway.gateway.seguranca;

import java.time.Clock;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.comum.Tempo;
import volvet.checkadv_gateway.gateway.cripto.CifradorAesGcm;

/** Operações sobre chaves HMAC: gerar, listar, revogar e escolher a chave de assinatura dos callbacks. */
@Component
public class ChavesGrupo {

    private final JdbcClient jdbc;
    private final CifradorAesGcm cifrador;
    private final Clock relogio;

    public ChavesGrupo(JdbcClient jdbc, CifradorAesGcm cifrador, Clock relogio) {
        this.jdbc = jdbc;
        this.cifrador = cifrador;
        this.relogio = relogio;
    }

    /** Segredo devolvido UMA vez; no banco só fica cifrado. */
    public record ChaveNova(String keyId, String segredoBase64) {
        @Override
        public String toString() {
            return "ChaveNova[keyId=" + keyId + ", segredo=***]";
        }
    }

    @Transactional
    public ChaveNova gerar(TipoChave tipo, String grupoPrefixo, String keyId) {
        String segredo = AssinadorHmac.gerarSegredoBase64();
        String cifrado = cifrador.cifrar(java.util.Base64.getDecoder().decode(segredo), ChaveHmac.contextoCifra(keyId));
        jdbc.sql("""
                INSERT INTO chave_hmac (key_id, tipo, grupo_prefixo, segredo_cifrado, chave_cifra_id, ativa, criada_em)
                VALUES (?, ?, ?, ?, ?, TRUE, ?)""")
                .params(keyId, tipo.name(), grupoPrefixo, cifrado, cifrador.chaveId(), Tempo.utc(relogio.instant()))
                .update();
        return new ChaveNova(keyId, segredo);
    }

    /** {@code grp-<prefixo>-<aaaa-mm>}, com sufixo -2, -3... se já existir. */
    @Transactional(readOnly = true)
    public String proximoKeyId(String grupoPrefixo) {
        String base = "grp-" + grupoPrefixo + "-" + java.time.YearMonth.now(relogio.withZone(Tempo.FUSO_API));
        String candidato = base;
        for (int i = 2; existe(candidato); i++) {
            candidato = base + "-" + i;
        }
        return candidato;
    }

    @Transactional(readOnly = true)
    public List<String> ativas(String grupoPrefixo) {
        return jdbc.sql("SELECT key_id FROM chave_hmac WHERE grupo_prefixo = ? AND ativa = TRUE AND revogada_em IS NULL ORDER BY criada_em")
                .param(grupoPrefixo).query(String.class).list();
    }

    /** @return false se a chave não existe, não é do grupo ou já estava revogada */
    @Transactional
    public boolean revogar(String grupoPrefixo, String keyId) {
        return jdbc.sql("""
                UPDATE chave_hmac SET ativa = FALSE, revogada_em = ?
                 WHERE key_id = ? AND grupo_prefixo = ? AND revogada_em IS NULL""")
                .params(Tempo.utc(relogio.instant()), keyId, grupoPrefixo).update() > 0;
    }

    /**
     * Chave usada para ASSINAR callbacks ao grupo: a ativa mais recente. Durante a
     * rotação, a instância aceita as duas, então assinar com a nova é seguro.
     */
    @Transactional(readOnly = true)
    public Optional<ChaveResolvida> paraAssinarCallback(String grupoPrefixo) {
        return jdbc.sql("""
                SELECT key_id, segredo_cifrado, chave_cifra_id FROM chave_hmac
                 WHERE grupo_prefixo = ? AND tipo = 'GRUPO' AND ativa = TRUE AND revogada_em IS NULL
                 ORDER BY criada_em DESC LIMIT 1""")
                .param(grupoPrefixo)
                .query((rs, n) -> new ChaveResolvida(rs.getString(1), TipoChave.GRUPO, grupoPrefixo,
                        new AssinadorHmac(cifrador.decifrar(rs.getString(2), rs.getString(3),
                                ChaveHmac.contextoCifra(rs.getString(1))))))
                .optional();
    }

    private boolean existe(String keyId) {
        return jdbc.sql("SELECT COUNT(*) FROM chave_hmac WHERE key_id = ?").param(keyId).query(Integer.class).single() > 0;
    }
}
