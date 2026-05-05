---
name: sistema-contabilidade-project-guide
description: Guide for understanding the sistema_contabilidade repository before making changes. Use when Codex needs a fast project map, module overview, page flow, build rules, profile/deploy behavior, storage/cache/security conventions, Sonar quality workflow, or architecture constraints for this Spring Boot accounting system.
---

# Sistema Contabilidade Project Guide

Use this skill at the start of work in this repository to avoid rediscovering the same context.

## Core Workflow

1. Read [references/project-analysis.md](references/project-analysis.md) first.
2. Confirm which module the task touches: `auth`, `usuario`, `item`, `home`, `relatorio`, `notificacao`, `security`, `rbac`, or static pages.
3. Preserve the enforced layering: `controller -> service -> repository`.
4. Before Maven in PowerShell, load Java 25 with `.\scripts\use-java25.ps1`.
5. Prefer `.\mvnw` and keep Spotless, tests, Checkstyle, SpotBugs, PMD, Error Prone, ArchUnit, JaCoCo, and Sonar clean.
6. If the task mentions query count, N+1, Prometheus, Grafana, Actuator, PDF, Redis, Docker, or hidden admin routes, inspect the related local files before changing code.

## Working Rules

- Treat `src/main/resources/templates` as the first-render server-side UI source of truth.
- Keep `src/main/resources/static` aligned because the static pages/assets still drive the frontend behavior and fallback flows.
- Expect backend-driven selects and cards in several static pages; check the page JS before assuming hardcoded options.
- Expect real auth to depend on `SC_SESSION`; `SC_TOKEN` is legacy compatibility, not the primary session path.
- Expect admin navigation to be hidden behind `AdminRouteService`; legacy `/admin`-style URLs may be intentionally blocked or redirected.
- For debugging or larger changes, also use the local skills `codex-debug`, `pragmatic-programmer`, and `refactoring-patterns`.
- For complexity/performance investigations, also consider `cyclomatic-complexity-spring` and `spring-query-monitor`.
- Query observability is local to this repo: `monitoring/query` tracks SQL count per request, adds `X-Query-Count`, and exports the Micrometer metric `http.server.query.count`.
- Request timing observability is local to this repo: `monitoring/http` adds `X-App-Time-Ms`, `Server-Timing`, slow-request logging, and Micrometer request duration metrics.
- Memory observability is local to this repo: `monitoring/memory` exposes heap/metaspace gauges and optional scheduled logging.
- The local observability stack lives under `observability/` with Prometheus, Grafana provisioning, dashboard JSON, and alert rules already versioned.
- Report PDF generation is local to this repo: `relatorio-financeiro.html` + `PlaywrightPdfService` + `ThymeleafTemplateRenderer`.
- Report web summaries are intentionally lighter than PDF details: the page endpoint returns `RelatorioFinanceiroResumoResponse`; the PDF path still uses the detailed factory.
- The project already has a local executive-report mock: `relatorio-executivo-exemplo.html`.
- Local Redis can be started with root `docker-compose.yml`; the app local profile points to `localhost:6379`, but the active Spring cache is still Caffeine unless explicitly changed.
- Keep secrets out of commits; the project imports `.env`, so environment overrides can silently change local behavior.
- Never print tokens/passwords from `.env`; mask values when inspecting them.

## Load More Context Only When Needed

- Read `src/test/java/com/sistema_contabilidade/architecture/ArchitectureRulesTest.java` when a change touches package boundaries or naming conventions.
- Read `src/main/java/com/sistema_contabilidade/security/config/SecurityConfig.java` when the task affects auth, static page access, API access, CORS, or CSRF.
- Read `src/main/java/com/sistema_contabilidade/security/service/AdminRouteService.java` and `src/main/java/com/sistema_contabilidade/security/util/SecurityPaths.java` when the task affects admin URLs, hidden routes, or admin frontend/API access.
- Read `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountFilter.java`, `QueryCountContext.java`, and `QueryCountStatementInspector.java` when the task affects performance diagnostics, SQL counting, headers, Prometheus, or query thresholds.
- Read `src/main/java/com/sistema_contabilidade/monitoring/http/RequestTimingFilter.java` and `src/main/java/com/sistema_contabilidade/monitoring/RequestMonitoringPathUtils.java` when the task affects request timing headers, slow-request logs, or HTTP duration metrics.
- Read `src/main/java/com/sistema_contabilidade/monitoring/memory/service/MemoryMonitoringService.java`, `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringMetrics.java`, and `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringProperties.java` when the task affects memory pressure diagnostics.
- Read `src/test/java/com/sistema_contabilidade/monitoring/query/QueryCountAuditIntegrationTest.java` and `QueryCountPrometheusIntegrationTest.java` when the task touches critical endpoint budgets or `/actuator/prometheus`.
- Read `src/main/java/com/sistema_contabilidade/usuario/controller/PaginaUsuarioController.java` when adding or changing static pages.
- Read `src/main/resources/templates/fragments/navbar.html`, `src/main/resources/static/partials/navbar.html`, `assets/css/navbar.css`, and `assets/js/navbar.js` when changing navbar behavior.
- Read `src/main/java/com/sistema_contabilidade/auth/controller/AuthController.java` and `src/main/resources/static/assets/js/auth-session.js` when changing login, logout, session bootstrap, refresh, cookies, or redirect behavior.
- Read `src/main/java/com/sistema_contabilidade/item/service/PdfUploadSecurityValidator.java` and storage services when the task affects upload, validation, file size, S3/local storage, or download headers.
- Read `src/main/java/com/sistema_contabilidade/item/service/ItemListService.java`, `ItemListSpecifications.java`, and `ItemRepository.java` when changing the comprovantes list, filters, pagination, or indexes.
- Read `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroService.java`, `RelatorioFinanceiroConsolidador.java`, `RelatorioFinanceiroPdfDataFactory.java`, `PlaywrightPdfService.java`, and `src/main/resources/templates/relatorio-financeiro.html` when the task affects reports or PDF output.
- Read `src/main/resources/application.properties`, `application-local.properties`, and `application-prod.properties` when behavior differs by environment, storage, cache, Redis, Actuator, Prometheus, or Docker/RDS.
- Read root `docker-compose.yml` before changing local Redis/container behavior.
- Read `observability/README.md`, `observability/docker-compose.yml`, and the Grafana/Prometheus provisioning files when the task mentions dashboards, alerts, scrape config, or local telemetry.
- Read `scripts/sonar-precommit.ps1` and `sonar-project.properties` when the task mentions SonarQube; the script relies on env tokens and those values must stay masked in logs.
