-- MySQL skill implementation baseline (safe, non-destructive)
-- Applied on 2026-03-19 to schema: sistema_contabilidade
-- References: A:\Projetos IA\skills\bd\mysql\SKILL.md + references/*.md

-- 1) Keep database default charset/collation aligned with utf8mb4 standard.
ALTER DATABASE `sistema_contabilidade`
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

-- 2) Composite index to optimize joins from role -> user mapping.
ALTER TABLE `usuario_roles`
  ADD INDEX `idx_usuario_roles_role_usuario` (`role_id`, `usuario_id`);

-- 3) Composite covering-style index for role-visible report item access.
ALTER TABLE `itens`
  ADD INDEX `idx_itens_criado_tipo_data_hora` (`criado_por_id`, `tipo`, `data`, `horario_criacao`);

-- 4) Session lifecycle index for frequent filters by status + expiration.
ALTER TABLE `sessoes_usuario`
  ADD INDEX `idx_sessoes_usuario_revogada_expira` (`revogada`, `expira_em`);
