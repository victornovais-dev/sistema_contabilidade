# Reports and Notifications Reference

## Relatorios

- Page: `relatorios.html`
- Web API: `/api/v1/relatorios/financeiro`
- PDF API: `/api/v1/relatorios/financeiro/pdf`
- Web endpoint returns lightweight `RelatorioFinanceiroResumoResponse`.
- PDF path uses detailed payload via `RelatorioFinanceiroPdfDataFactory`.
- Web summary aggregation uses `RelatorioFinanceiroConsolidador`.
- `RelatorioFinanceiroService` acts as orchestrator.
- `PlaywrightPdfService` renders Thymeleaf PDF template and embeds logo as data URI.
- Central PDF template: `relatorio-financeiro.html`.
- Executive visual mock exists: `relatorio-executivo-exemplo.html`.
- `Despesas por categoria` uses circular chart and fixed palette.
- Download from report page should download directly, without `about:blank` or `file://`.

## Financial Consolidation

`RelatorioFinanceiroConsolidador` calculates:

- financial receitas
- estimable receitas
- considered despesas
- legal/accounting despesas
- total despesas
- limited category percentages
- final balance

## Notificacoes

- Page: `notificacoes.html`
- Main API: `/api/v1/notificacoes`
- Every receita synchronizes a persistent notification.
- If receita is removed, related notification is removed.
- If receita becomes verified, notification remains on page.
- Navbar badge counts only unchecked/unverified notifications.
- `Valor lancado` reflects items with green check.
- Page is restricted to `ADMIN` and `CONTABIL`.
