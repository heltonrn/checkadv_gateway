# CheckADV Gateway

Componentes que ficam entre as instâncias do CheckADV e a Meta (WhatsApp Cloud API).
Contrato completo: `gateway-whatsapp-apis-e-dependencias.md` (projeto no Claude).

| Módulo | O que é | Porta |
| --- | --- | --- |
| `gateway-contrato` | DTOs, enums e `AssinadorHmac`. Sem Spring; também entra como dependência no CheckADV | — |
| `gateway-app` | Gateway WhatsApp: API v1, webhook, worker, callbacks, admin. Único que guarda segredos | 8081 |
| `resolvedor-app` | Resolvedor de links `/v/{token}` (público, sem segredos) | 8082 |

Pacote raiz: `volvet.checkadv_gateway` (`.contrato`, `.gateway`, `.resolvedor`).

## Rodar localmente

Requisitos: JDK 25, MySQL 8.0+ ou MariaDB 10.6+.

```sql
CREATE DATABASE checkadv_gateway CHARACTER SET utf8mb4 COLLATE utf8mb4_bin;
CREATE USER 'checkadv_gateway'@'localhost' IDENTIFIED BY 'troque';
GRANT ALL ON checkadv_gateway.* TO 'checkadv_gateway'@'localhost';
```

1. Gerar a chave mestra: rodar `volvet.checkadv_gateway.gateway.ferramentas.GerarChave` com o argumento `mestra`.
2. Variáveis de ambiente do `gateway-app`:

| Variável | Padrão | Observação |
| --- | --- | --- |
| `GATEWAY_CHAVE_MESTRA` | (obrigatória) | AES-256 em Base64. Nunca no banco nem no Git |
| `GATEWAY_CHAVE_MESTRA_ID` | `k1` | Gravado junto de cada valor cifrado |
| `GATEWAY_DB_URL` | `jdbc:mysql://localhost:3306/checkadv_gateway?serverTimezone=UTC` | |
| `GATEWAY_DB_USUARIO` / `GATEWAY_DB_SENHA` | `checkadv_gateway` / vazio | |
| `GATEWAY_PORTA` | `8081` | |
| `GATEWAY_CALLBACK_PERMITIR_HTTP` | `false` | `true` só no desenvolvimento, para callback em `http://` |

3. `./mvnw -pl gateway-app -am spring-boot:run` (o Flyway cria o esquema).
4. Criar um grupo e uma chave (até a API `/admin` existir):

```sql
INSERT INTO grupo_instancia (prefixo, nome, base_url_interna, ativo, criado_em, atualizado_em)
VALUES ('A1B2', 'Grupo 001', 'http://localhost:8080', TRUE, UTC_TIMESTAMP(3), UTC_TIMESTAMP(3));
```

Depois rodar `GerarChave GRUPO grp-A1B2-2026-10 A1B2` com as mesmas variáveis de ambiente
e executar o `INSERT` que ela imprime. O segredo aparece uma única vez.

## Autenticação (API v1 e admin)

```
X-Key-Id:      grp-A1B2-2026-10
X-Timestamp:   <epoch em segundos, ±300 s>
X-Assinatura:  sha256=<hex HMAC-SHA256(segredo, texto canônico)>

texto canônico = timestamp \n METODO \n caminho?query (como enviado, codificado) \n hex(SHA256(corpo))
```

Do lado da instância, usar o próprio `AssinadorHmac` do `gateway-contrato`:

```java
var assinador = AssinadorHmac.deSegredoBase64(segredo);
var cab = assinador.cabecalhos(keyId, Instant.now().getEpochSecond(), "POST", "/v1/envios", corpoBytes);
cab.comoMapa().forEach(request::header);
```

Os vetores de teste fixos estão em `AssinadorHmacTest`.

## Estado (Fase 1 — Meta simulada)

Todas as APIs do contrato estão implementadas, exceto o webhook real da Meta (`/webhook/meta`
responde 501 até a Fase 2). A Meta é **simulada** (`app.meta.modo=simulado`): nenhuma mensagem
real sai, mas o fluxo inteiro acontece — fila, worker, status assíncronos e callbacks.

| Área | Pacote | O que faz |
| --- | --- | --- |
| Contas | `conta` | `PUT/GET /v1/contas/{id}`; conta presa ao grupo que a registrou |
| WhatsApp | `conta` | Onboarding, desconexão (cancela a fila, apaga o token) e template |
| Cota | `cota` | Reserva atômica dia + período; `BLOQUEAR` ou `PERMITIR_COBRAR_EXTRA` |
| Envios | `envio` | Idempotência, validações, telefone e token cifrados, cancelamento |
| Transições | `envio.TransicoesEnvio` | Único ponto que muda status: UPDATE condicional + histórico + callback + devolução de cota |
| Worker | `fila` | Fila no banco (SKIP LOCKED), Meta fora de transação, backoff, varredor de lease, purga |
| Meta | `meta` | Interface `MetaCliente`; `MetaClienteSimulado` |
| Callbacks | `callback` | Outbox na mesma transação; entrega assinada com retentativas por 24 h |
| Opt-out | `optout` | Bloqueio por conta (só hash do telefone); remoção auditada |
| Admin | `admin` | Grupos, chaves (com rotação), reenvio de callback, diagnóstico |
| Agendamentos | `agendamento` | Worker 1 s, callbacks 2 s, varredor 30 s, purga 1 h |

### Comportamento da Meta simulada (pelo final do telefone)

| Final | Resultado |
| --- | --- |
| `0000` | Falha definitiva (131026, número sem WhatsApp) → FALHOU, cota devolvida |
| `0001` | Falha temporária (130429) → volta para a fila com espera (30 s, 2 min…) |
| `0002` | Incerto (timeout) → INCERTO |
| `0003` | Aceito, depois "failed" (131049) → FALHOU |
| outros | Aceito → ENTREGUE em 2 s → LIDO em 5 s |

O onboarding aceita qualquer `code` (exceto começando com `FALHA`) e já deixa o número CONECTADO
e o template APROVADO.

## Testes

```
mvnw verify
```

- **Sem banco:** roda os testes de contrato, segurança e camada web (37). Os de integração são pulados.
- **Com banco (recomendado):** crie um schema só para testes e defina, na Run Configuration do
  JUnit ou no ambiente:

| Variável | Exemplo |
| --- | --- |
| `GATEWAY_TEST_DB_URL` | `jdbc:mysql://localhost:3306/checkadv_gateway_teste?serverTimezone=UTC` |
| `GATEWAY_TEST_DB_USUARIO` / `GATEWAY_TEST_DB_SENHA` | usuário com permissão total nesse schema |

Os testes de integração (28) sobem a aplicação inteira, **esvaziam as tabelas** antes de cada teste
e cobrem o fluxo ponta a ponta, incluindo callbacks assinados recebidos por um servidor HTTP local.

Coleção Postman em `postman/` para testes manuais (assina as requisições sozinha).

## Pendências

- **`contaId` precisa ser globalmente único entre grupos** (cada grupo tem seu banco). Sugestão:
  a instância envia um UUID da `Conta`, não o id auto-incremento.
- Fase 2: `MetaCliente` real (Graph API), webhook com verificação de `X-Hub-Signature-256`
  e onboarding assíncrono.
- Mensagens recebidas do cliente (opt-out por palavra-chave) entram com o webhook real; o
  `ServicoOptout.registrar` já existe.
- Fonte do roteamento do Resolvedor: view `v_roteamento` ou `/interno/roteamento`.
- **MariaDB com o driver do MySQL descarta frações de segundo** (o MariaDB se apresenta como
  "5.5.5"). Funciona, mas em produção com MariaDB use `org.mariadb.jdbc:mariadb-java-client`
  e URL `jdbc:mariadb://`. Com MySQL 8 não há problema.
- Rotação da chave mestra: o envio não guarda o id da chave usada no telefone/parâmetros
  (hoje assume a chave atual).
- Limite por número em memória (por réplica); com várias réplicas de worker, trocar por Bucket4j
  com backend no banco.
