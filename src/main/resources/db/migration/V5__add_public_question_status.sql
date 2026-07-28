ALTER TABLE duvidas_publicas
  ADD COLUMN status VARCHAR(20) NOT NULL DEFAULT 'PENDENTE';

CREATE INDEX idx_duvidas_publicas_status_recebida
  ON duvidas_publicas (status, recebida_em);
