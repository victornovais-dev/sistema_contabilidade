ALTER DATABASE CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;

CREATE TABLE permissoes (
  id BINARY(16) NOT NULL,
  nome VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_permissoes_nome UNIQUE (nome)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE roles (
  id BINARY(16) NOT NULL,
  nome VARCHAR(100) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_roles_nome UNIQUE (nome)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE usuarios (
  id BINARY(16) NOT NULL,
  email VARCHAR(255) NOT NULL,
  nome VARCHAR(255) NOT NULL,
  senha VARCHAR(255) NOT NULL,
  version BIGINT NULL,
  cognito_groups_hash VARCHAR(128) NULL,
  cognito_sub VARCHAR(80) NULL,
  cognito_synced_at DATETIME(6) NULL,
  cognito_username VARCHAR(120) NULL,
  PRIMARY KEY (id),
  CONSTRAINT idx_usuarios_email UNIQUE (email),
  CONSTRAINT idx_usuarios_cognito_sub UNIQUE (cognito_sub),
  CONSTRAINT idx_usuarios_cognito_username UNIQUE (cognito_username)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE role_permissoes (
  role_id BINARY(16) NOT NULL,
  permissao_id BINARY(16) NOT NULL,
  PRIMARY KEY (role_id, permissao_id),
  KEY idx_role_permissoes_permissao (permissao_id),
  CONSTRAINT fk_role_permissoes_role FOREIGN KEY (role_id) REFERENCES roles (id),
  CONSTRAINT fk_role_permissoes_permissao FOREIGN KEY (permissao_id) REFERENCES permissoes (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE usuario_roles (
  usuario_id BINARY(16) NOT NULL,
  role_id BINARY(16) NOT NULL,
  PRIMARY KEY (usuario_id, role_id),
  KEY idx_usuario_roles_role_usuario (role_id, usuario_id),
  CONSTRAINT fk_usuario_roles_usuario FOREIGN KEY (usuario_id) REFERENCES usuarios (id),
  CONSTRAINT fk_usuario_roles_role FOREIGN KEY (role_id) REFERENCES roles (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE sessoes_usuario (
  id BINARY(16) NOT NULL,
  usuario_id BINARY(16) NOT NULL,
  criada_em DATETIME(6) NOT NULL,
  expira_em DATETIME(6) NOT NULL,
  atualizada_em DATETIME(6) NOT NULL,
  auth_provider ENUM('COGNITO', 'LOCAL') NOT NULL,
  auth_username VARCHAR(120) NULL,
  cognito_sub VARCHAR(80) NULL,
  refresh_token_cifrado TINYTEXT NULL,
  groups_snapshot TINYTEXT NULL,
  groups_hash VARCHAR(128) NULL,
  revogada_em DATETIME(6) NULL,
  revogada BIT(1) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_sessao_usuario_id (usuario_id),
  KEY idx_sessao_expira_em (expira_em),
  KEY idx_sessao_cognito_sub (cognito_sub),
  KEY idx_sessoes_usuario_revogada_expira (revogada, expira_em)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE item_descricoes (
  id BIGINT NOT NULL AUTO_INCREMENT,
  tipo ENUM('DESPESA', 'RECEITA') NOT NULL,
  nome VARCHAR(160) NOT NULL,
  ordem INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_item_descricoes_tipo_nome UNIQUE (tipo, nome)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE item_tipos_documento (
  id BIGINT NOT NULL AUTO_INCREMENT,
  nome VARCHAR(80) NOT NULL,
  ordem INT NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT uk_item_tipos_documento_nome UNIQUE (nome)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE itens (
  id BINARY(16) NOT NULL,
  version BIGINT NULL,
  valor DECIMAL(15, 2) NOT NULL,
  data DATE NOT NULL,
  horario_criacao DATETIME(6) NOT NULL,
  caminho_arquivo_pdf VARCHAR(500) NULL,
  descricao VARCHAR(120) NULL,
  tipo_documento VARCHAR(80) NULL,
  numero_documento VARCHAR(50) NULL,
  razao_social VARCHAR(150) NULL,
  razao_social_busca VARCHAR(200) NULL,
  cnpj_cpf VARCHAR(20) NULL,
  observacao VARCHAR(500) NULL,
  forma_pagamento ENUM('AVISTA', 'PARCELADO') NULL,
  verificado BIT(1) NOT NULL,
  role_nome VARCHAR(100) NULL,
  tipo ENUM('DESPESA', 'RECEITA') NOT NULL,
  criado_por_id BINARY(16) NULL,
  PRIMARY KEY (id),
  KEY idx_itens_horario_id (horario_criacao, id),
  KEY idx_itens_role_horario_id (role_nome, horario_criacao, id),
  KEY idx_itens_criado_tipo_data_hora (criado_por_id, tipo, data, horario_criacao),
  FULLTEXT KEY ft_itens_razao_social_busca (razao_social_busca),
  CONSTRAINT fk_itens_criado_por FOREIGN KEY (criado_por_id) REFERENCES usuarios (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE itens_arquivos (
  id BINARY(16) NOT NULL,
  caminho_arquivo_pdf VARCHAR(500) NOT NULL,
  item_id BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_itens_arquivos_item (item_id),
  CONSTRAINT fk_itens_arquivos_item FOREIGN KEY (item_id) REFERENCES itens (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE itens_parcelas_pagamento (
  id BINARY(16) NOT NULL,
  numero INT NOT NULL,
  valor_parcela DECIMAL(15, 2) NOT NULL,
  paga BIT(1) NOT NULL,
  conta_origem_pagamento ENUM('CONTA_DC', 'CONTA_FEFC', 'CONTA_FP') NULL,
  caminho_arquivo_pdf VARCHAR(500) NULL,
  item_id BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_itens_parcelas_pagamento_item (item_id),
  CONSTRAINT fk_itens_parcelas_pagamento_item FOREIGN KEY (item_id) REFERENCES itens (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE itens_parcelas_pagamento_arquivos (
  id BINARY(16) NOT NULL,
  caminho_arquivo_pdf VARCHAR(500) NOT NULL,
  parcela_pagamento_id BINARY(16) NOT NULL,
  PRIMARY KEY (id),
  KEY idx_itens_parcelas_pagamento_arquivos_parcela (parcela_pagamento_id),
  CONSTRAINT fk_itens_parcelas_pagamento_arquivos_parcela
    FOREIGN KEY (parcela_pagamento_id) REFERENCES itens_parcelas_pagamento (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

CREATE TABLE notificacoes (
  id BINARY(16) NOT NULL,
  item_id BINARY(16) NOT NULL,
  role_nome VARCHAR(100) NOT NULL,
  descricao VARCHAR(120) NULL,
  razao_social_nome VARCHAR(150) NULL,
  valor DECIMAL(15, 2) NOT NULL,
  criado_em DATETIME(6) NOT NULL,
  limpa BIT(1) NOT NULL,
  PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
