# Items Reference

## Adicionar Comprovante

- Page: `adicionar_comprovante.html`
- Main API: `POST /api/v1/itens`
- Dynamic selects:
  - `/api/v1/itens/roles`
  - `/api/v1/itens/descricoes?tipo=...`
  - `/api/v1/itens/tipos-documento?tipo=...`

Rules:

- PDF attachment is required on creation.
- Current PDF max size: 20 MB.
- Validation exists in frontend and backend.
- `Extrato Bancario` is a special UI type limited to:
  - `CONTA FEFC`
  - `CONTA FP`
  - `CONTA DC`
- `CONTABIL` cannot access the add page.
- CPF must be unique; CNPJ can repeat.
- Drag-and-drop should use a dedicated area and not consume global document drops.
- Binary PDF validation happens in `PdfUploadSecurityValidator`.
- Local/S3 storage uses sanitized names and final UUID-based keys.

## Lista de Comprovantes

- Page: `lista_comprovantes.html`
- Main API: `GET /api/v1/itens`

Pagination input:

- `page`
- `pageSize`
- `role`
- `tipo`
- `dataInicio`
- `dataFim`
- `descricao`
- `razao`

Response envelope:

- `items`
- `page`
- `pageSize`
- `totalItems`
- `totalPages`
- `hasNext`
- `hasPrevious`

Rules:

- Default order: `horarioCriacao desc, id desc`.
- `descricao` uses exact filter.
- `razao` uses search path based on `razaoSocialBusca`.
- Current hot path uses `Slice`, avoiding per-request `count(*)`.
- MySQL/MariaDB can upgrade search to FULLTEXT when available.

Indexes:

- `idx_itens_horario_id (horario_criacao, id)`
- `idx_itens_role_horario_id (role_nome, horario_criacao, id)`

## Item Card

Supports:

- observation
- single file download
- ZIP download
- additional attachments
- deletion
- verification check

Authorization/rules:

- `CONTABIL` can access details and read/update endpoints when scoped.
- `CONTABIL` cannot delete.
- verified items cannot be deleted.
- `SUPPORT` can mark red -> green, but cannot revert green -> red.
- `CANDIDATO` cannot change verification.
- `Item` uses optimistic locking with `@Version`.
- legacy items with `version = null` are normalized before verification changes.
- verified receitas update navbar badge but remain visible on notification page.
- attachment modal shows a per-file error card for rejected files.
