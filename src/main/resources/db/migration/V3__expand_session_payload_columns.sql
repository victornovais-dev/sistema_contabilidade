ALTER TABLE sessoes_usuario
  MODIFY COLUMN refresh_token_cifrado TEXT NULL,
  MODIFY COLUMN groups_snapshot TEXT NULL;
