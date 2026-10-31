-- =====================================================================
-- Ajustes para a implementação das APIs (Fase 1)
-- =====================================================================

-- Janelas de cota usadas na reserva: necessárias para devolver a cota
-- na janela certa quando o envio é cancelado ou falha.
ALTER TABLE envio_whatsapp
    ADD COLUMN cota_dia            DATE NULL COMMENT 'janela DIARIA reservada (fuso America/Sao_Paulo)' AFTER excedente,
    ADD COLUMN cota_periodo_inicio DATE NULL COMMENT 'janela MENSAL reservada; nulo se a conta não tem limite de período' AFTER cota_dia,
    ADD INDEX ix_envio_conta_status (conta_id, status);

-- teste.ping não pertence a nenhuma conta; prazo_ate permite reenvio manual
-- de um callback que já tinha estourado as 24 h.
ALTER TABLE callback_pendente
    MODIFY COLUMN conta_id VARCHAR(64) NULL,
    ADD COLUMN prazo_ate DATETIME(3) NULL COMMENT 'depois disso vira CALLBACK_FALHOU' AFTER proxima_tentativa;

-- Opt-out por conta (escritório): o cliente pediu para não receber mensagens daquele número.
CREATE TABLE optout (
    conta_id            VARCHAR(64)   NOT NULL,
    telefone_hash       VARCHAR(64)   NOT NULL,
    telefone_mascarado  VARCHAR(32)   NOT NULL,
    ativo               BOOLEAN       NOT NULL,
    origem              VARCHAR(32)   NOT NULL COMMENT 'PALAVRA_CHAVE | SOLICITACAO_ESCRITORIO',
    registrado_em       DATETIME(3)   NOT NULL,
    removido_em         DATETIME(3)   NULL,
    evidencia_remocao   VARCHAR(500)  NULL COMMENT 'JSON da evidência do novo consentimento',
    CONSTRAINT pk_optout PRIMARY KEY (conta_id, telefone_hash),
    CONSTRAINT fk_optout_conta FOREIGN KEY (conta_id) REFERENCES conta (conta_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;

-- Trilha das operações administrativas e de dados sensíveis (quem, o quê, quando)
CREATE TABLE auditoria (
    id          BIGINT        NOT NULL AUTO_INCREMENT,
    ocorrido_em DATETIME(3)   NOT NULL,
    ator        VARCHAR(64)   NOT NULL COMMENT 'keyId que fez a chamada',
    acao        VARCHAR(64)   NOT NULL,
    alvo        VARCHAR(128)  NULL,
    detalhe     VARCHAR(500)  NULL,
    CONSTRAINT pk_auditoria PRIMARY KEY (id),
    INDEX ix_auditoria_data (ocorrido_em)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_bin;
