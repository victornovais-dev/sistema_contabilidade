-- Ajuste nomes conforme o schema real do projeto.

ALTER TABLE usuario
    ADD COLUMN cognito_sub VARCHAR(80);

CREATE UNIQUE INDEX uk_usuario_cognito_sub
    ON usuario (cognito_sub);

-- Opcional: controlar sincronização
ALTER TABLE usuario
    ADD COLUMN cognito_groups_hash VARCHAR(128);

ALTER TABLE usuario
    ADD COLUMN cognito_synced_at TIMESTAMP NULL;
