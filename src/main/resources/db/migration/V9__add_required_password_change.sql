ALTER TABLE usuarios
  ADD COLUMN troca_senha_obrigatoria BIT(1) NOT NULL DEFAULT b'0';
