CREATE TABLE solicitacoes_privacidade (
    id BINARY(16) NOT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    protocolo VARCHAR(32) NOT NULL,
    nome_titular VARCHAR(512) NOT NULL,
    email_titular VARCHAR(512) NOT NULL,
    email_bidx VARCHAR(64) NOT NULL,
    organizacao VARCHAR(512) NOT NULL,
    vinculo VARCHAR(40) NOT NULL,
    tipo VARCHAR(40) NOT NULL,
    escopos VARCHAR(255) NOT NULL,
    canal_resposta VARCHAR(20) NOT NULL,
    referencia_titular VARCHAR(512) NULL,
    status VARCHAR(40) NOT NULL,
    recebida_em DATE NOT NULL,
    prazo DATE NOT NULL,
    responsavel VARCHAR(512) NOT NULL,
    identidade_verificada BOOLEAN NOT NULL DEFAULT FALSE,
    descricao VARCHAR(4096) NOT NULL,
    retencao_legal BOOLEAN NOT NULL DEFAULT FALSE,
    motivo_retencao VARCHAR(2048) NULL,
    versao_aviso VARCHAR(20) NOT NULL,
    criada_em DATETIME(6) NOT NULL,
    atualizada_em DATETIME(6) NOT NULL,
    deleted_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_sol_priv_protocolo (protocolo),
    KEY idx_sol_priv_email_bidx (email_bidx),
    KEY idx_sol_priv_status_prazo (status, prazo)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE solicitacoes_privacidade_eventos (
    id BINARY(16) NOT NULL,
    solicitacao_id BINARY(16) NOT NULL,
    titulo VARCHAR(120) NOT NULL,
    descricao VARCHAR(2048) NOT NULL,
    ator VARCHAR(512) NOT NULL,
    ocorrido_em DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_sol_priv_eventos_solicitacao (solicitacao_id, ocorrido_em),
    CONSTRAINT fk_sol_priv_evento_solicitacao
        FOREIGN KEY (solicitacao_id) REFERENCES solicitacoes_privacidade (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
