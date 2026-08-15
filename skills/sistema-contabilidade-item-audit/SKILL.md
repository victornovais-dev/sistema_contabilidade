---
name: sistema-contabilidade-item-audit
description: Implement or review immutable database auditing for comprovantes/items in sistema_contabilidade. Use for tracking who created, changed, verified, annotated, attached files to, or soft-deleted an item; recording field diffs, client IP, user agent, request trace ID; and retaining physical PDFs in S3 after logical removal.
---

# Sistema Contabilidade Item Audit

Use this skill for item-audit work. Preserve `controller -> service -> repository`.

## First Read

1. Read `references/audit-contract.md`.
2. Read `skills/sistema-contabilidade-project-guide/sistema-contabilidade-items/SKILL.md`.
3. Inspect only affected files:
   - `item/model/Item.java` and attachment/payment models;
   - `item/controller/ItemController.java`;
   - `item/repository/ItemRepository.java`;
   - `security/service/RequestFingerprintService.java`;
   - `security/filter/RequestContextMdcFilter.java`;
   - relevant Flyway migrations and item tests.

## Implementation Workflow

1. Add Flyway schema and immutable JPA model/repository for audit events.
2. Create an item mutation/audit service. Do not add auditing business logic directly to controllers.
3. Capture authenticated actor, request IP, user agent and trace ID once per request.
4. Capture a pre-mutation snapshot; apply mutation; calculate minimal field diff; persist audit event in same transaction.
5. For item soft delete, persist `EXCLUIDO` with snapshot before calling `itemRepository.delete`.
6. Keep S3 objects for normal attachment, replacement and item deletions. Delete only files uploaded by a transaction that later fails before persistence.
7. Add authorized history API/UI only after read/write audit contracts and tests are in place.

## Non-Negotiable Rules

- Audit events are append-only: no update, delete, cascade delete or soft delete.
- Audit history survives item lifecycle. Store `item_id` as indexed value without database cascade.
- Encrypt PII and sensitive before/after values. Never persist password/token/PDF content.
- `Admin*` Cognito API names are not actors; audit actor comes from authenticated application user.
- Treat `X-Forwarded-For` as client IP only behind trusted ALB/proxy. Do not expose backend directly to clients.
- Preserve existing authorization, optimistic locking, notification synchronization and after-commit list-cache invalidation.
- Do not manufacture historical audit events for changes before deployment.

## Validation

- Test each mutation action, actor, timestamp, exact diff and audit immutability.
- Test rollback: failed item mutation leaves no audit event; failed pre-persistence upload removes only new orphan object.
- Test soft delete: item/attachment disappear from regular queries, audit remains, S3 object remains.
- Test non-admin audit history access returns `403`.
- Run `ItemControllerWebMvcTest`, focused audit integration tests, then project Maven quality gates.
