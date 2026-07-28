UPDATE itens item
JOIN (
  SELECT usuario_role.usuario_id, MIN(UPPER(TRIM(role.nome))) AS role_nome
  FROM usuario_roles usuario_role
  JOIN roles role ON role.id = usuario_role.role_id
  WHERE UPPER(TRIM(role.nome)) NOT IN ('ADMIN', 'CONTABIL', 'MANAGER', 'SUPPORT', 'CANDIDATO')
  GROUP BY usuario_role.usuario_id
  HAVING COUNT(DISTINCT UPPER(TRIM(role.nome))) = 1
) candidato_role ON candidato_role.usuario_id = item.criado_por_id
JOIN usuario_roles usuario_candidato_role ON usuario_candidato_role.usuario_id = item.criado_por_id
JOIN roles role_candidato
  ON role_candidato.id = usuario_candidato_role.role_id
  AND UPPER(TRIM(role_candidato.nome)) = 'CANDIDATO'
SET item.role_nome = candidato_role.role_nome
WHERE UPPER(TRIM(item.role_nome)) = 'CANDIDATO';

UPDATE notificacoes notificacao
JOIN itens item ON item.id = notificacao.item_id
SET notificacao.role_nome = UPPER(TRIM(item.role_nome))
WHERE UPPER(TRIM(notificacao.role_nome)) = 'CANDIDATO'
  AND UPPER(TRIM(item.role_nome)) <> 'CANDIDATO';
