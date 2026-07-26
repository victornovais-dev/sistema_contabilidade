# Reports and Notifications Reference

## Relatorios

- Page: `relatorios.html`
- Web API: `/api/v1/relatorios/financeiro`
- PDF API: `/api/v1/relatorios/financeiro/pdf`
- Web endpoint returns lightweight `RelatorioFinanceiroResumoResponse`.
- PDF path uses detailed payload via `RelatorioFinanceiroPdfDataFactory`.
- Web summary aggregation uses `RelatorioFinanceiroConsolidador`.
- Web summaries can use `RelatorioResumoCacheService`, which stores only
  `RelatorioFinanceiroResumoResponse` JSON in Valkey for 30 seconds and limits entries to 128 KiB.
- Cache keys isolate authorized roles, normalized role filter, normalized filters and a global
  version incremented after successful item mutations.
- Sticky-writer requests bypass the cache so the writing session never receives a pre-write
  summary. Valkey failures calculate from the database.
- `RelatorioFinanceiroService` acts as orchestrator.
- `PlaywrightPdfService` renders Thymeleaf PDF template and embeds logo as data URI.
- Central PDF template: `relatorio-financeiro.html`.
- Executive visual mock exists: `relatorio-executivo-exemplo.html`.
- `Despesas por categoria` uses circular chart and fixed palette.
- Download from report page should download directly, without `about:blank` or `file://`.
- `relatorios.html` renders `Despesas Executadas` and then `Resumo Financeiro`.
- The web and PDF endpoints apply the same report scope: admins can select an available candidate role; other users only receive their authorized roles.

## Financial Consolidation

`RelatorioFinanceiroConsolidador` calculates:

- financial receitas
- estimable receitas
- considered despesas
- legal/accounting despesas
- total despesas
- limited category percentages
- final balance
- paid accounts from paid expense installments
- accounts payable: `despesasConsideradas + despesasAdvocaciaContabilidade - contasPagas`
- account balances: revenue from `CONTA DC`, `CONTA FEFC` and `CONTA FP` less paid installments from each source account
- The report page shows `Contas pagas`, `Contas a pagar` and a separate balance card for `CONTA DC`, `CONTA FEFC` and `CONTA FP`.

## Notificacoes

- Page: `notificacoes.html`
- Main API: `/api/v1/notificacoes`
- Every receita synchronizes a persistent notification.
- `GET /api/v1/notificacoes` reads only the persistent notification read model in a read-only transaction.
- Notification creation, update and removal happen only during item mutations; listing performs no reconciliation or DML.
- If receita is removed, related notification is removed.
- If receita becomes verified, notification remains on page.
- Navbar badge counts only unchecked/unverified notifications.
- `Valor lancado` reflects items with green check.
- Page is restricted to `ADMIN` and `CONTABIL`.
