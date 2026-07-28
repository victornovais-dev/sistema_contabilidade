CREATE TABLE duvidas_publicas (
  id BINARY(16) NOT NULL,
  nome VARCHAR(120) NOT NULL,
  email VARCHAR(255) NOT NULL,
  duvida VARCHAR(1200) NOT NULL,
  recebida_em DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_duvidas_publicas_recebida_em (recebida_em)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
