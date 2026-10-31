package volvet.checkadv_gateway.gateway.ferramentas;

import java.util.Base64;

import volvet.checkadv_gateway.contrato.seguranca.AssinadorHmac;
import volvet.checkadv_gateway.gateway.cripto.CifradorAesGcm;
import volvet.checkadv_gateway.gateway.seguranca.ChaveHmac;

/**
 * Utilitário de linha de comando (rodar pela IDE) enquanto a API /admin não existe.
 *
 * <pre>
 * GerarChave mestra
 *     → imprime uma chave mestra nova para GATEWAY_CHAVE_MESTRA
 *
 * GerarChave OPERADOR op-helton-2026-10
 * GerarChave GRUPO grp-A1B2-2026-10 A1B2
 *     → com GATEWAY_CHAVE_MESTRA e GATEWAY_CHAVE_MESTRA_ID no ambiente, imprime o
 *       segredo (mostrado só uma vez) e o INSERT da chave cifrada
 * </pre>
 */
public final class GerarChave {

    private GerarChave() {
    }

    public static void main(String[] args) {
        if (args.length >= 1 && args[0].equals("mestra")) {
            System.out.println("GATEWAY_CHAVE_MESTRA=" + CifradorAesGcm.gerarChaveBase64());
            return;
        }
        if (args.length < 2 || (args[0].equals("GRUPO") && args.length < 3)) {
            System.err.println("Uso: GerarChave mestra | GerarChave OPERADOR <keyId> | GerarChave GRUPO <keyId> <prefixo>");
            System.exit(2);
        }
        String tipo = args[0];
        String keyId = args[1];
        String prefixo = tipo.equals("GRUPO") ? args[2] : null;

        String chaveMestra = System.getenv("GATEWAY_CHAVE_MESTRA");
        String chaveMestraId = System.getenv().getOrDefault("GATEWAY_CHAVE_MESTRA_ID", "k1");
        if (chaveMestra == null || chaveMestra.isBlank()) {
            System.err.println("Defina GATEWAY_CHAVE_MESTRA (gere com: GerarChave mestra)");
            System.exit(2);
        }

        var cifrador = new CifradorAesGcm(chaveMestraId, chaveMestra);
        String segredo = AssinadorHmac.gerarSegredoBase64();
        // Guarda-se cifrado o segredo em bytes (o que vira a chave HMAC), não o texto Base64.
        String cifrado = cifrador.cifrar(Base64.getDecoder().decode(segredo), ChaveHmac.contextoCifra(keyId));

        System.out.println("keyId   = " + keyId);
        System.out.println("segredo = " + segredo + "   (guarde na instância/cofre; não será mostrado de novo)");
        System.out.println();
        if (prefixo != null) {
            System.out.println("-- o grupo " + prefixo + " precisa existir em grupo_instancia antes");
        }
        System.out.printf(
                "INSERT INTO chave_hmac (key_id, tipo, grupo_prefixo, segredo_cifrado, chave_cifra_id, ativa, criada_em)%n"
                        + "VALUES ('%s', '%s', %s, '%s', '%s', TRUE, UTC_TIMESTAMP(3));%n",
                keyId, tipo, prefixo == null ? "NULL" : "'" + prefixo + "'", cifrado, chaveMestraId);
    }
}
