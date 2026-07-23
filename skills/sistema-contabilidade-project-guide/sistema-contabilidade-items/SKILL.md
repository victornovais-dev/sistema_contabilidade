---
name: sistema-contabilidade-items
description: Use for sistema_contabilidade comprovantes/items, item list, upload/download, PDF validation, storage local/S3, filters, pagination, verification, observations, razaoSocial search and item authorization rules.
---

# Sistema Contabilidade Items

Use this skill when the task touches comprovantes, item APIs, upload/download, PDF validation, payment installments, list filtering, pagination, verification, observation, attachments, search or item authorization.

## First Read

1. Read `references/items.md`.
2. Inspect the relevant controller/service/repository files.
3. If the task includes UI changes, also read `skills/sistema-contabilidade-project-guide/sistema-contabilidade-ui-pages/SKILL.md`.
4. If it includes performance or query count, also read `skills/sistema-contabilidade-project-guide/sistema-contabilidade-observability/SKILL.md`.

## Main Files

- `src/main/java/com/sistema_contabilidade/item/controller/ItemController.java`
- `src/test/java/com/sistema_contabilidade/item/controller/ItemControllerWebMvcTest.java`
- `src/main/java/com/sistema_contabilidade/item/service/PdfUploadSecurityValidator.java`
- Storage services for local/S3 storage
- `src/main/java/com/sistema_contabilidade/item/service/ItemListService.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemListPageQuery.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemListPageRepositoryImpl.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemListSpecifications.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemRepository.java`
- `src/main/java/com/sistema_contabilidade/item/config/ItemRazaoSocialSearchInitializer.java`
- `src/main/java/com/sistema_contabilidade/item/config/ItemRazaoSocialSearchDatabaseSupport.java`
- `src/main/java/com/sistema_contabilidade/common/util/SearchTextNormalizer.java`
- `src/main/java/com/sistema_contabilidade/item/dto/ItemPagamentoUpdateRequest.java`
- `src/main/java/com/sistema_contabilidade/item/dto/ItemPagamentoParcelaUpdateRequest.java`
- `src/main/java/com/sistema_contabilidade/item/model/ItemParcelaPagamento.java`
- `src/main/java/com/sistema_contabilidade/item/model/ItemParcelaPagamentoArquivo.java`

## Workflow

### Upload/create item

1. Inspect `ItemController`.
2. Inspect frontend page and JS if UX/validation changes.
3. Inspect `PdfUploadSecurityValidator`.
4. Inspect storage local/S3 services.
5. Preserve PDF-only validation on frontend and backend.

### List/filter/search

1. Inspect `ItemListService`.
2. Inspect `ItemListPageRepositoryImpl`.
3. Check pagination query contract.
4. Preserve `Slice` optimization unless there is a reason to reintroduce `count(*)`.
5. For `razaoSocialNome`, inspect normalized search support and FULLTEXT behavior.

### Verification/authorization

1. Inspect `ItemController` authorization helpers.
2. Preserve `CONTABIL` read/update access where scoped.
3. Preserve delete restrictions.
4. Check optimistic locking and legacy `version = null` handling.

### Payment installments

1. Inspect `PATCH /api/v1/itens/{id}/pagamento` in `ItemController` and its DTOs.
2. Preserve `AVISTA` or `PARCELADO`, with one to four installments.
3. Keep installment values editable only for `PARCELADO`; recompute totals from saved values.
4. Preserve multiple PDFs per installment and defer removals until the payment save succeeds.

## Validation

- Run `ItemControllerWebMvcTest`.
- Run focused item/list/storage tests.
- If query behavior changes, run query count tests or inspect query count headers.
