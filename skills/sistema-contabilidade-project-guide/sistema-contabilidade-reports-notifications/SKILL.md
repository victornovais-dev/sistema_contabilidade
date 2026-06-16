---
name: sistema-contabilidade-reports-notifications
description: Use for sistema_contabilidade financial reports, Playwright PDF generation, relatorio pages, report summaries, notification sync, notification badge and receita/despesa aggregation behavior.
---

# Sistema Contabilidade Reports and Notifications

Use this skill for reports, financial summaries, PDF generation, Playwright rendering, notifications, notification badge behavior, receitas/despesas aggregation and the report UI.

## First Read

1. Read `references/reports-notifications.md`.
2. Inspect service/factory/template files relevant to the task.
3. If the task includes item verification or receita sync, also read `skills/sistema-contabilidade-items/SKILL.md`.
4. If the task includes visual/page changes, also read `skills/sistema-contabilidade-ui-pages/SKILL.md`.

## Main Files

- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroService.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroConsolidador.java`
- `src/main/java/com/sistema_contabilidade/relatorio/repository/RelatorioResumoCategoriaRow.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroPdfDataFactory.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/PlaywrightPdfService.java`
- `src/main/resources/templates/relatorio-financeiro.html`
- `src/main/resources/templates/relatorio-executivo-exemplo.html`
- Notification service/controller files under `notificacao`

## Workflow

### Web report

1. Inspect report endpoint and `RelatorioFinanceiroService`.
2. Preserve lightweight web summary response.
3. Keep aggregation in the database when currently optimized.
4. Check frontend layout only after backend contract is clear.

### PDF report

1. Inspect `RelatorioFinanceiroPdfDataFactory`.
2. Inspect `relatorio-financeiro.html`.
3. Inspect `PlaywrightPdfService`.
4. Preserve the separate detailed PDF path.

### Notifications

1. Inspect notification sync from item/receita changes.
2. Preserve badge rule: only unverified notifications count.
3. Preserve page rule: verified receita notifications remain visible.

## Validation

- Run focused report/PDF tests.
- Run notification tests.
- If item verification is involved, run item tests too.
