-- =====================================================================
-- CheckADV Gateway — esquema inicial
-- MySQL 8.0+ / MariaDB 10.6+ (SKIP LOCKED). Datas sempre em UTC.
-- Collation utf8mb4_bin: prefixos, tokens e referências são sensíveis a
-- maiúsculas/minúsculas (Base64URL).
-- =====================================================================

-- Grupos de instâncias do CheckADV (cada grupo = um banco próprio e um prefixo de 4 caracteres)
CREATE TABLE grupo_instancia (
    prefixo                    VARCHAR(4)    NOT NULL,
    nome                       VARCHAR(100)  NOT NULL,
    base_url_interna           VARCHAR(255)  NOT NULL,
    callback_url               VARCHAR(500)  NULL,
    hosts_callback_permitidos  VARCHAR(1000) NULL COMMENT 'lista separada por vírgula (anti-SSRF)',
    ativo                      BOOLEAN       NOT NULL DEFAULT TRUE,
    criado_em                  DATETIME(3)   NOT NULL,
    atualizado_em              DATETIME(3)   NOT NULL,
    CONSTRAINT pk_grupo_instancia PRIMARY KEY (prefixo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Única coisa que o Resolvedor de links pode ler (usuário de banco só com SELECT nesta view)
CREATE VIEW v_roteamento AS
    SELECT prefixo, base_url_interna, ativo FROM grupo_instancia;

-- Chaves HMAC de grupo (API v1 e callbacks) e de operador (/admin)
CREATE TABLE chave_hmac (
    key_id           VARCHAR(64)  NOT NULL,
    tipo             VARCHAR(16)  NOT NULL COMMENT 'GRUPO | OPERADOR',
    grupo_prefixo    VARCHAR(4)   NULL,
    segredo_cifrado  VARCHAR(255) NOT NULL COMMENT 'AES-GCM, AAD chave_hmac:<key_id>',
    chave_cifra_id   VARCHAR(32)  NOT NULL,
    ativa            BOOLEAN      NOT NULL DEFAULT TRUE,
    criada_em        DATETIME(3)  NOT NULL,
    revogada_em      DATETIME(3)  NULL,
    CONSTRAINT pk_chave_hmac PRIMARY KEY (key_id),
    CONSTRAINT fk_chave_hmac_grupo FOREIGN KEY (grupo_prefixo) REFERENCES grupo_instancia (prefixo),
    CONSTRAINT ck_chave_hmac_tipo CHECK (
        (tipo = 'GRUPO' AND grupo_prefixo IS NOT NULL) OR (tipo = 'OPERADOR' AND grupo_prefixo IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Conta (escritório contratante) registrada por um grupo. conta_id é opaco para o Gateway
-- e precisa ser globalmente único entre grupos (ver pendência no README).
CREATE TABLE conta (
    conta_id       VARCHAR(64)  NOT NULL,
    grupo_prefixo  VARCHAR(4)   NOT NULL,
    nome_exibicao  VARCHAR(120) NOT NULL,
    ativa          BOOLEAN      NOT NULL DEFAULT TRUE,
    criado_em      DATETIME(3)  NOT NULL,
    atualizado_em  DATETIME(3)  NOT NULL,
    CONSTRAINT pk_conta PRIMARY KEY (conta_id),
    CONSTRAINT fk_conta_grupo FOREIGN KEY (grupo_prefixo) REFERENCES grupo_instancia (prefixo),
    INDEX ix_conta_grupo (grupo_prefixo)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Número WhatsApp do escritório (Embedded Signup) e status do template na WABA dele
CREATE TABLE whatsapp_conta_config (
    conta_id          VARCHAR(64)   NOT NULL,
    status            VARCHAR(20)   NOT NULL COMMENT 'NAO_CONECTADA | CONECTANDO | CONECTADA | DESCONECTADA | SUSPENSA',
    waba_id           VARCHAR(32)   NULL,
    phone_number_id   VARCHAR(32)   NULL,
    numero_exibicao   VARCHAR(32)   NULL,
    nome_verificado   VARCHAR(120)  NULL,
    qualidade         VARCHAR(16)   NULL,
    limite_mensagens  VARCHAR(32)   NULL,
    token_cifrado     VARCHAR(2048) NULL COMMENT 'AES-GCM, AAD whatsapp_token:<conta_id>',
    chave_cifra_id    VARCHAR(32)   NULL,
    template_nome     VARCHAR(128)  NULL,
    template_idioma   VARCHAR(10)   NULL,
    template_status   VARCHAR(16)   NULL COMMENT 'PENDENTE | APROVADO | REJEITADO | PAUSADO',
    template_motivo   VARCHAR(255)  NULL,
    criado_em         DATETIME(3)   NOT NULL,
    atualizado_em     DATETIME(3)   NOT NULL,
    CONSTRAINT pk_whatsapp_conta_config PRIMARY KEY (conta_id),
    CONSTRAINT fk_whatsapp_conta FOREIGN KEY (conta_id) REFERENCES conta (conta_id),
    CONSTRAINT uq_whatsapp_phone_number UNIQUE (phone_number_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Fila de envio (outbox). O worker reserva com SELECT ... FOR UPDATE SKIP LOCKED.
CREATE TABLE envio_whatsapp (
    envio_id            VARCHAR(36)   NOT NULL,
    conta_id            VARCHAR(64)   NOT NULL,
    referencia_externa  VARCHAR(64)   NOT NULL,
    hash_conteudo       VARCHAR(64)   NOT NULL COMMENT 'SHA-256 do conteúdo normalizado (idempotência)',
    telefone_cifrado    VARCHAR(255)  NOT NULL,
    telefone_hash       VARCHAR(64)   NOT NULL COMMENT 'HMAC do E.164 (busca e contagem sem decifrar)',
    telefone_mascarado  VARCHAR(32)   NOT NULL,
    template_nome       VARCHAR(128)  NOT NULL,
    template_idioma     VARCHAR(10)   NOT NULL,
    parametros_json     TEXT          NULL COMMENT 'contém o token do link: cifrar/purgar após estado terminal',
    status              VARCHAR(16)   NOT NULL,
    sequencia           INT           NOT NULL DEFAULT 0,
    tentativas          INT           NOT NULL DEFAULT 0,
    proxima_tentativa   DATETIME(3)   NOT NULL,
    lease_ate           DATETIME(3)   NULL,
    wamid               VARCHAR(128)  NULL,
    categoria_cobranca  VARCHAR(20)   NULL,
    cobravel            BOOLEAN       NULL,
    excedente           BOOLEAN       NOT NULL DEFAULT FALSE,
    erro_categoria      VARCHAR(32)   NULL,
    erro_mensagem       VARCHAR(255)  NULL,
    erro_codigo_meta    VARCHAR(16)   NULL,
    criado_em           DATETIME(3)   NOT NULL,
    atualizado_em       DATETIME(3)   NOT NULL,
    purgado_em          DATETIME(3)   NULL,
    CONSTRAINT pk_envio_whatsapp PRIMARY KEY (envio_id),
    CONSTRAINT fk_envio_conta FOREIGN KEY (conta_id) REFERENCES conta (conta_id),
    CONSTRAINT uq_envio_referencia UNIQUE (conta_id, referencia_externa),
    CONSTRAINT uq_envio_wamid UNIQUE (wamid),
    INDEX ix_envio_fila (status, proxima_tentativa),
    INDEX ix_envio_lease (status, lease_ate),
    INDEX ix_envio_unicos (conta_id, criado_em, telefone_hash)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

CREATE TABLE envio_historico (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    envio_id     VARCHAR(36)  NOT NULL,
    status       VARCHAR(16)  NOT NULL,
    sequencia    INT          NOT NULL,
    ocorrido_em  DATETIME(3)  NOT NULL,
    detalhe      VARCHAR(255) NULL,
    CONSTRAINT pk_envio_historico PRIMARY KEY (id),
    CONSTRAINT fk_historico_envio FOREIGN KEY (envio_id) REFERENCES envio_whatsapp (envio_id),
    INDEX ix_historico_envio (envio_id, ocorrido_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Outbox de callbacks: gravado na MESMA transação da mudança de status
CREATE TABLE callback_pendente (
    id                 BIGINT       NOT NULL AUTO_INCREMENT,
    evento_id          VARCHAR(36)  NOT NULL,
    grupo_prefixo      VARCHAR(4)   NOT NULL,
    conta_id           VARCHAR(64)  NOT NULL,
    tipo               VARCHAR(40)  NOT NULL,
    envio_id           VARCHAR(36)  NULL,
    sequencia          INT          NULL,
    payload_json       TEXT         NOT NULL,
    status             VARCHAR(20)  NOT NULL COMMENT 'PENDENTE | ENTREGUE | CALLBACK_FALHOU',
    tentativas         INT          NOT NULL DEFAULT 0,
    proxima_tentativa  DATETIME(3)  NOT NULL,
    ultimo_erro        VARCHAR(255) NULL,
    criado_em          DATETIME(3)  NOT NULL,
    entregue_em        DATETIME(3)  NULL,
    CONSTRAINT pk_callback_pendente PRIMARY KEY (id),
    CONSTRAINT uq_callback_evento UNIQUE (evento_id),
    CONSTRAINT uq_callback_envio_sequencia UNIQUE (envio_id, sequencia),
    CONSTRAINT fk_callback_grupo FOREIGN KEY (grupo_prefixo) REFERENCES grupo_instancia (prefixo),
    INDEX ix_callback_fila (status, proxima_tentativa)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Plano vigente da conta (definido pelo sistema principal via PUT /v1/contas/{id}/cota)
CREATE TABLE cota_config (
    conta_id          VARCHAR(64) NOT NULL,
    periodo_inicio    DATE        NOT NULL,
    periodo_fim       DATE        NOT NULL COMMENT 'inclusivo, fuso America/Sao_Paulo',
    limite            INT         NOT NULL,
    limite_diario     INT         NOT NULL,
    politica_excesso  VARCHAR(24) NOT NULL COMMENT 'BLOQUEAR | PERMITIR_COBRAR_EXTRA',
    atualizado_em     DATETIME(3) NOT NULL,
    CONSTRAINT pk_cota_config PRIMARY KEY (conta_id),
    CONSTRAINT fk_cota_config_conta FOREIGN KEY (conta_id) REFERENCES conta (conta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Contadores atômicos: UPDATE ... SET usado = usado + 1 WHERE ... AND usado < limite
CREATE TABLE cota_uso (
    conta_id       VARCHAR(64) NOT NULL,
    escopo         VARCHAR(8)  NOT NULL COMMENT 'MENSAL | DIARIA',
    janela_inicio  DATE        NOT NULL,
    janela_fim     DATE        NOT NULL,
    limite         INT         NOT NULL,
    usado          INT         NOT NULL DEFAULT 0,
    excedente      INT         NOT NULL DEFAULT 0,
    CONSTRAINT pk_cota_uso PRIMARY KEY (conta_id, escopo, janela_inicio),
    CONSTRAINT fk_cota_uso_conta FOREIGN KEY (conta_id) REFERENCES conta (conta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Webhook da Meta: corpo bruto gravado antes do processamento assíncrono
CREATE TABLE webhook_evento (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    chave_dedup   VARCHAR(128) NOT NULL,
    recebido_em   DATETIME(3)  NOT NULL,
    corpo         MEDIUMTEXT   NOT NULL,
    status        VARCHAR(16)  NOT NULL COMMENT 'RECEBIDO | PROCESSADO | DESCARTADO | ERRO',
    processado_em DATETIME(3)  NULL,
    erro          VARCHAR(255) NULL,
    CONSTRAINT pk_webhook_evento PRIMARY KEY (id),
    CONSTRAINT uq_webhook_dedup UNIQUE (chave_dedup),
    INDEX ix_webhook_status (status, recebido_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- ShedLock (varredor de lease, purga, reconciliação noturna)
CREATE TABLE shedlock (
    name        VARCHAR(64)  NOT NULL,
    lock_until  TIMESTAMP(3) NOT NULL,
    locked_at   TIMESTAMP(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    locked_by   VARCHAR(255) NOT NULL,
    CONSTRAINT pk_shedlock PRIMARY KEY (name)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
