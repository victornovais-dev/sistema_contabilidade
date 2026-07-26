DELETE notificacao
FROM notificacoes notificacao
LEFT JOIN itens item ON item.id = notificacao.item_id
WHERE item.id IS NULL
   OR item.tipo <> 'RECEITA'
   OR item.role_nome IS NULL
   OR TRIM(item.role_nome) = '';

UPDATE notificacoes notificacao
JOIN (
  SELECT item_id, MAX(CAST(limpa AS UNSIGNED)) AS alguma_limpa
  FROM notificacoes
  GROUP BY item_id
) estado ON estado.item_id = notificacao.item_id
SET notificacao.limpa = estado.alguma_limpa = 1;

DELETE duplicada
FROM notificacoes duplicada
JOIN notificacoes mantida
  ON mantida.item_id = duplicada.item_id
 AND mantida.id < duplicada.id;

UPDATE notificacoes notificacao
JOIN itens item ON item.id = notificacao.item_id
SET notificacao.role_nome = UPPER(TRIM(item.role_nome)),
    notificacao.descricao = item.descricao,
    notificacao.razao_social_nome = item.razao_social,
    notificacao.valor = item.valor,
    notificacao.criado_em = item.horario_criacao;

INSERT INTO notificacoes (
  id,
  item_id,
  role_nome,
  descricao,
  razao_social_nome,
  valor,
  criado_em,
  limpa
)
SELECT UUID_TO_BIN(UUID()),
       item.id,
       UPPER(TRIM(item.role_nome)),
       item.descricao,
       item.razao_social,
       item.valor,
       item.horario_criacao,
       b'0'
FROM itens item
LEFT JOIN notificacoes notificacao ON notificacao.item_id = item.id
WHERE item.tipo = 'RECEITA'
  AND item.role_nome IS NOT NULL
  AND TRIM(item.role_nome) <> ''
  AND notificacao.id IS NULL;

ALTER TABLE notificacoes
  ADD CONSTRAINT uk_notificacoes_item_id UNIQUE (item_id),
  ADD CONSTRAINT fk_notificacoes_item_id
    FOREIGN KEY (item_id) REFERENCES itens (id);
