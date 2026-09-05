CREATE TABLE item_auditorias (
  id BINARY(16) NOT NULL,
  item_id BINARY(16) NOT NULL,
  acao ENUM(
    'CRIADO',
    'ALTERADO',
    'OBSERVACAO_ALTERADA',
    'VERIFICACAO_ALTERADA',
    'PAGAMENTO_ALTERADO',
    'ARQUIVO_INCLUIDO',
    'ARQUIVO_REMOVIDO',
    'EXCLUIDO'
  ) NOT NULL,
  ator_id BINARY(16) NOT NULL,
  ator_nome VARCHAR(512) NOT NULL COMMENT 'Encrypted actor-name snapshot',
  ator_email VARCHAR(512) NOT NULL COMMENT 'Encrypted actor-email snapshot',
  ocorrido_em DATETIME(6) NOT NULL COMMENT 'UTC',
  ip_origem VARCHAR(256) NOT NULL COMMENT 'Encrypted client IP',
  user_agent VARCHAR(1024) NULL,
  trace_id CHAR(32) NOT NULL,
  alteracoes MEDIUMTEXT NOT NULL COMMENT 'Encrypted canonical JSON diff',
  snapshot_item MEDIUMTEXT NULL COMMENT 'Encrypted canonical JSON item snapshot',
  PRIMARY KEY (id),
  KEY idx_item_auditorias_item_ocorrido (item_id, ocorrido_em, id),
  KEY idx_item_auditorias_ocorrido (ocorrido_em)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci
  COMMENT = 'Append-only item audit history';

-- item_id and ator_id intentionally have no foreign keys. Audit history must survive
-- the lifecycle of both the audited item and the actor snapshot.
