# Item Audit Contract

## Event schema

Create `item_auditorias` with:

| Column | Rule |
|---|---|
| `id` | UUID primary key |
| `item_id` | indexed BINARY(16), no cascade foreign key |
| `acao` | `CRIADO`, `ALTERADO`, `EXCLUIDO`, `VERIFICACAO_ALTERADA`, `OBSERVACAO_ALTERADA`, `ARQUIVO_INCLUIDO`, `ARQUIVO_REMOVIDO`, `PAGAMENTO_ALTERADO` |
| `ator_id` | authenticated local user ID |
| `ator_nome`, `ator_email` | encrypted actor snapshots |
| `ocorrido_em` | UTC timestamp |
| `ip_origem` | encrypted client IP |
| `user_agent` | request user-agent, bounded length |
| `trace_id` | validated request trace ID |
| `alteracoes` | encrypted canonical JSON diff |
| `snapshot_item` | encrypted canonical JSON for creation/deletion |

Do not add an entity lifecycle listener as the primary implementation. It cannot reliably capture request actor, request metadata, explicit action semantics or before/after values.

## Diff rules

Record only changed fields. Include before/after for value, date, type, description, document data, role/campaign, verification, observation and payment/installments. For files, record metadata only: attachment ID, display name, storage key and included/removed status. Never record PDF binary content.

`VERIFICACAO_ALTERADA` must record `verificado: false -> true` or `true -> false`.

`OBSERVACAO_ALTERADA` must record sanitized observation before/after.

## S3 retention

Normal logical removal must not call `ArquivoStorageService.deletarPdf` or S3 `deleteObject`. Keep object key in audit snapshot so authorized recovery remains possible. Physical deletion is allowed only for a newly uploaded file when enclosing creation/update transaction fails before item persistence.

## API contract

Add `GET /api/v1/itens/{id}/auditoria` after audit persistence exists. Return timestamp, actor snapshot, action and authorized diff. Apply the same campaign scope and privileged access policy as item detail; no write endpoints for audit history.

## Existing baseline

Current item creation stores `criado_por_id`; item changes do not store an editor; Hibernate soft delete stores `deleted_at` but not deleting actor. Existing events before migration are not reconstructed.
