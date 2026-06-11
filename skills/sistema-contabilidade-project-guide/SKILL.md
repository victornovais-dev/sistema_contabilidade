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

- Treat `src/main/resources/templates` as the first-render server-side UI source of truth for authenticated pages, but remember the public auth pages `/login` and `/primeiro_acesso` are served directly from `src/main/resources/static`.
- Keep `src/main/resources/static` aligned because the static pages/assets still drive the frontend behavior and fallback flows.
- Treat `/` as an auth-aware redirect, not a standalone landing page: anonymous users go to `/login`; authenticated users go to `/home`.
- Expect Cognito-aware auth in production by default: `app.auth.provider` comes from `APP_AUTH_PROVIDER`, and `application-prod.properties` now defaults to `cognito`.
- Expect a dedicated first-access flow for Cognito users: `NEW_PASSWORD_REQUIRED` redirects to `/primeiro_acesso`, not an inline login form.
- Expect auth orchestration to be provider-based: `AuthService` delegates login/refresh/logout to `AuthProviderStrategyResolver`, which selects `LocalAuthProviderStrategy` or `CognitoAuthProviderStrategy`.
- Expect backend-driven selects and cards in several static pages; check the page JS before assuming hardcoded options.
- Expect real auth to depend on `SC_SESSION`; `SC_TOKEN` is legacy compatibility, not the primary session path.
- Expect first-access completion to depend on the encrypted `SC_LOGIN_CHALLENGE` cookie scoped to `/api/v1/auth`; login may return `202 Accepted` with challenge metadata before any session cookie exists.
- Expect production auth/CORS to depend on `APP_CORS_ALLOWED_ORIGINS`; missing production domains breaks `/api/v1/auth/refresh` and login with `Invalid CORS request`, even when ALB/DNS/certificate are fine.
- Expect Cognito startup to fail fast in `prod` when required config is missing; `AWS_REGION`, `COGNITO_USER_POOL_ID`, `COGNITO_APP_CLIENT_ID`, `SESSION_CRYPTO_SECRET`, `JWT_EC_PRIVATE_KEY`, and `JWT_EC_PUBLIC_KEY` are validated on boot when provider is `cognito`, while `COGNITO_APP_CLIENT_SECRET` remains optional and only enables `SECRET_HASH` handling if present.
- Expect Cognito app-client/IAM coupling in production incidents: login needs the app client auth flows and the backend IAM identity needs Cognito permissions such as `AdminInitiateAuth`, `AdminRespondToAuthChallenge`, `AdminGetUser`, `AdminListGroupsForUser`, `InitiateAuth`, and `ListGroups` for the admin role catalog.
- Expect admin navigation to be hidden behind `AdminRouteService`; legacy `/admin`-style URLs may be intentionally blocked or redirected.
- Expect Cognito group sync to materialize missing local roles on login/admin sync: `CognitoRoleSyncService` now normalizes the group name and creates the corresponding local `Role` when absent instead of failing with "Grupo Cognito sem role local correspondente".
- Expect role/permission refresh bugs to involve both email and id cache keys: `CustomUserDetailsService` now warms/evicts `userDetails` cache entries for both keys, so permission changes should no longer require container restart, but an already logged-in user may still need a fresh login/session refresh.
- For debugging or larger changes, also use the local skills `codex-debug`, `pragmatic-programmer`, and `refactoring-patterns`.
- For complexity/performance investigations, also consider `cyclomatic-complexity-spring` and `spring-query-monitor`.
- Query observability is local to this repo: `monitoring/query` tracks SQL count per request, adds `X-Query-Count`, and exports the Micrometer metric `http.server.query.count`.
- Request timing observability is local to this repo: `monitoring/http` adds `X-App-Time-Ms`, `Server-Timing`, slow-request logging, and Micrometer request duration metrics.
- Memory observability is local to this repo: `monitoring/memory` exposes heap/metaspace gauges and optional scheduled logging.
- Current list/report performance work already moved key hot paths away from full-entity loading: `lista_comprovantes` uses the dedicated paginated projection path in `ItemListPageRepositoryImpl`, now backed by `Slice` instead of `Page` to avoid per-request `count(*)`, and `relatorios` web summaries aggregate by category in the database via `RelatorioResumoCategoriaRow`.
- Current candidate-role filtering is shared infrastructure, not page-local logic: `CandidateRoleCatalogService` is reused by `item`, `notificacao`, and `relatorio` to expose only candidate groups/roles to admin filters and scoped users.
- Current item authorization nuance matters for debugging: `CONTABIL` now has read/update access to item details, verification, observation, and file endpoints through the scoped access helper in `ItemController`, but delete remains forbidden.
- User admin screens now have a Cognito-aware role catalog: `GET /api/v1/admin/roles/disponiveis` merges local roles with Cognito groups, and the create/update user JS should target that route instead of the legacy local-only role list.
- The home/user identity path is Cognito-aware: if the local display name is blank or equal to the email, `UsuarioService` refreshes the profile from Cognito and prefers the `name` attribute.
- Production data-health issues already observed in this repo include legacy `usuarios.version = null`, zero dates in `sessoes_usuario.atualizada_em`, and undersized session text columns for encrypted refresh snapshots; if auth/session bugs appear only in prod, inspect schema/data before blaming Cognito.
- Current `razaoSocialNome` search no longer uses the old raw `UPPER(... ) LIKE '%...%'` path: the project now maintains a normalized `razaoSocialBusca` field, backfills it on startup, and upgrades to MySQL/MariaDB `FULLTEXT` search when available; if candidate-name filtering regresses, inspect `SearchTextNormalizer`, `ItemRazaoSocialSearchInitializer`, `ItemRazaoSocialSearchDatabaseSupport`, and the paginated repository implementation together.
- When touching auth caching, remember the runtime proxy gotcha already hit in this repo: SpEL cache keys on proxied methods should prefer positional parameters like `#p0.email` instead of relying on compiled parameter names such as `#usuario.email`.
- Static asset delivery is still worth validating separately from HTML/session responses: the app enables `server.compression` for CSS/JS/JSON, but CDN/proxy layers can still defeat caching or compression in prod-like environments, so check browser headers at the edge instead of assuming the application settings survived deployment.
- Current AWS/CDN deployment gotcha: when CloudFront fronts the ALB, the `/assets/*` behavior may need `Origin request policy = Managed-AllViewer` for ALB-backed origins; otherwise static assets can fail with `502` even while the HTML route works.
- Current AWS/TLS deployment gotcha: `www.sacsdigital.com.br` may still fail behind CloudFront if the ALB listener certificate does not cover `www`; fixing only the CloudFront certificate is not enough when the origin TLS handshake still lands on the ALB hostname/certificate chain.
- Versioned frontend assets now encode the version in the filename (`navbar-...css`, `auth-session-...js`, `lista_comprovantes-...css`) instead of `?v=` query params; keep template/static references synchronized when renaming.
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
- Read `src/main/java/com/sistema_contabilidade/usuario/controller/PaginaUsuarioController.java` when adding or changing static pages or the root redirect flow.
- Read `src/main/resources/static/primeiro_acesso.html` and `src/main/resources/static/assets/js/primeiro_acesso-20260605-cognito-new-password-page-1.js` when the task affects first-login password change, Cognito challenges, or public auth pages beyond `/login`.
- Read `src/main/resources/templates/adicionar_comprovante.html`, `src/main/resources/static/adicionar_comprovante.html`, and `src/main/resources/static/assets/js/adicionar_comprovante-20260427-auth-ready-1.js` when the task affects drag-and-drop upload, receipt creation UX, or browser crashes triggered by dropping files from unusual sources such as `.zip` previews.
- Read `src/main/resources/templates/fragments/navbar.html`, `src/main/resources/static/partials/navbar.html`, `src/main/resources/static/assets/css/navbar-20260420-navbar-notification-count-fix-3.css`, and `src/main/resources/static/assets/js/navbar-20260502-startup-perf-1.js` when changing navbar behavior.
- Read `src/main/java/com/sistema_contabilidade/auth/controller/AuthController.java` and `src/main/resources/static/assets/js/auth-session-20260502-startup-perf-1.js` when changing login, logout, session bootstrap, refresh, cookies, or redirect behavior.
- Read `src/main/java/com/sistema_contabilidade/auth/config/AwsCognitoConfig.java`, `AuthProviderStartupValidator.java`, `AuthProviderProperties.java`, `CognitoProperties.java`, and `src/main/resources/application*.properties` when the task affects provider selection, Cognito boot, or production env validation.
- Read `src/main/java/com/sistema_contabilidade/auth/service/AuthProviderStrategyResolver.java`, `LocalAuthProviderStrategy.java`, `CognitoAuthProviderStrategy.java`, `CognitoIdentitySyncService.java`, `CognitoRoleSyncService.java`, `CognitoUserManagementService.java`, `CognitoSecretHashService.java`, and `LoginChallengeCookieService.java` when the task affects login, first access, provider branching, Cognito profile sync, or admin user management.
- Read `src/main/java/com/sistema_contabilidade/security/service/CustomUserDetailsService.java` when touching login warmup, user-details cache population, or auth-related cache annotations.
- Read `src/main/java/com/sistema_contabilidade/item/controller/ItemController.java` and `src/test/java/com/sistema_contabilidade/item/controller/ItemControllerWebMvcTest.java` when the task affects item endpoint authorization, especially `CONTABIL` behavior on read/update/delete flows.
- Read `src/main/java/com/sistema_contabilidade/item/service/PdfUploadSecurityValidator.java` and storage services when the task affects upload, validation, file size, S3/local storage, or download headers.
- Read `src/main/java/com/sistema_contabilidade/item/service/ItemListService.java`, `ItemListPageQuery.java`, `ItemListPageRepositoryImpl.java`, `ItemListSpecifications.java`, `ItemRepository.java`, `ItemRazaoSocialSearchInitializer.java`, `ItemRazaoSocialSearchDatabaseSupport.java`, and `SearchTextNormalizer.java` when changing the comprovantes list, candidate-name filters, pagination, or search indexes.
- Read `src/main/java/com/sistema_contabilidade/rbac/controller/AdminController.java`, `src/main/java/com/sistema_contabilidade/rbac/service/RoleService.java`, and `src/main/java/com/sistema_contabilidade/auth/service/CognitoGroupCatalogService.java` when the task affects user-role pickers, Cognito groups, or admin role APIs.
- Read `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroService.java`, `RelatorioFinanceiroConsolidador.java`, `RelatorioResumoCategoriaRow.java`, `RelatorioFinanceiroPdfDataFactory.java`, `PlaywrightPdfService.java`, and `src/main/resources/templates/relatorio-financeiro.html` when the task affects reports or PDF output.
- Read `src/main/resources/application.properties`, `application-local.properties`, and `application-prod.properties` when behavior differs by environment, storage, cache, Redis, Actuator, Prometheus, Docker/RDS, or production CORS; `app.security.cors.allowed-origins` comes from `APP_CORS_ALLOWED_ORIGINS`.
- Read static page heads plus `SecurityConfig.java` and any MVC resource config before changing cache/compression strategy for CSS/JS; the repo currently mixes versioned assets with security-driven no-store behavior and now uses filename-based asset versioning instead of query params.
- Read root `docker-compose.yml` before changing local Redis/container behavior.
- Read CloudFront behavior/origin settings plus ALB listener certificates when the task mentions CDN, `502` on static assets, or hostname-specific failures for `root` vs `www`; in this project, successful CloudFront adoption has already depended on both `Managed-AllViewer` for `/assets/*` and an ALB certificate that covers `www`.
- Read `observability/README.md`, `observability/docker-compose.yml`, and the Grafana/Prometheus provisioning files when the task mentions dashboards, alerts, scrape config, or local telemetry.
- Read `scripts/sonar-precommit.ps1` and `sonar-project.properties` when the task mentions SonarQube; the script relies on env tokens and those values must stay masked in logs.
