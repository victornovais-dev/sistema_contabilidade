ALTER TABLE usuarios
  DROP INDEX idx_usuarios_email,
  DROP INDEX idx_usuarios_cognito_sub,
  DROP INDEX idx_usuarios_cognito_username,
  MODIFY COLUMN nome VARCHAR(512) NOT NULL,
  MODIFY COLUMN email VARCHAR(512) NOT NULL,
  MODIFY COLUMN senha VARCHAR(512) NOT NULL,
  MODIFY COLUMN cognito_sub VARCHAR(256) NULL,
  MODIFY COLUMN cognito_username VARCHAR(256) NULL,
  MODIFY COLUMN cognito_groups_hash VARCHAR(256) NULL,
  ADD COLUMN email_bidx CHAR(64) NULL,
  ADD COLUMN cognito_sub_bidx CHAR(64) NULL,
  ADD COLUMN cognito_username_bidx CHAR(64) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL,
  ADD CONSTRAINT uk_usuarios_email_bidx UNIQUE (email_bidx),
  ADD CONSTRAINT uk_usuarios_cognito_sub_bidx UNIQUE (cognito_sub_bidx),
  ADD CONSTRAINT uk_usuarios_cognito_username_bidx UNIQUE (cognito_username_bidx);

ALTER TABLE sessoes_usuario
  MODIFY COLUMN auth_username VARCHAR(256) NULL,
  MODIFY COLUMN cognito_sub VARCHAR(256) NULL,
  MODIFY COLUMN groups_hash VARCHAR(256) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE duvidas_publicas
  MODIFY COLUMN nome VARCHAR(512) NOT NULL,
  MODIFY COLUMN email VARCHAR(512) NOT NULL,
  MODIFY COLUMN duvida VARCHAR(2048) NOT NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE itens
  MODIFY COLUMN caminho_arquivo_pdf VARCHAR(1024) NULL,
  MODIFY COLUMN tipo_documento VARCHAR(256) NULL,
  MODIFY COLUMN numero_documento VARCHAR(256) NULL,
  MODIFY COLUMN razao_social VARCHAR(512) NULL,
  MODIFY COLUMN cnpj_cpf VARCHAR(256) NULL,
  MODIFY COLUMN observacao VARCHAR(1024) NULL,
  ADD COLUMN cnpj_cpf_bidx CHAR(64) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL,
  ADD INDEX idx_itens_cnpj_cpf_bidx (cnpj_cpf_bidx);

ALTER TABLE itens_arquivos
  MODIFY COLUMN caminho_arquivo_pdf VARCHAR(1024) NOT NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE itens_parcelas_pagamento
  MODIFY COLUMN caminho_arquivo_pdf VARCHAR(1024) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE itens_parcelas_pagamento_arquivos
  MODIFY COLUMN caminho_arquivo_pdf VARCHAR(1024) NOT NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE notificacoes
  MODIFY COLUMN descricao VARCHAR(512) NULL,
  MODIFY COLUMN razao_social_nome VARCHAR(512) NULL,
  ADD COLUMN deleted_at DATETIME(6) NULL;

ALTER TABLE permissoes ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE roles ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE item_descricoes ADD COLUMN deleted_at DATETIME(6) NULL;
ALTER TABLE item_tipos_documento ADD COLUMN deleted_at DATETIME(6) NULL;
