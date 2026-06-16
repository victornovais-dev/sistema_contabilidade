# File Lookup Index

Use this only after routing to a specialized skill.

## Architecture and Quality

- `src/test/java/com/sistema_contabilidade/architecture/ArchitectureRulesTest.java`
- `scripts/sonar-precommit.ps1`
- `sonar-project.properties`

## Security, Auth and Admin

- `src/main/java/com/sistema_contabilidade/security/config/SecurityConfig.java`
- `src/main/java/com/sistema_contabilidade/security/service/AdminRouteService.java`
- `src/main/java/com/sistema_contabilidade/security/util/SecurityPaths.java`
- `src/main/java/com/sistema_contabilidade/security/service/CustomUserDetailsService.java`
- `src/main/java/com/sistema_contabilidade/auth/controller/AuthController.java`
- `src/main/java/com/sistema_contabilidade/auth/config/AwsCognitoConfig.java`
- `src/main/java/com/sistema_contabilidade/auth/config/AuthProviderStartupValidator.java`
- `src/main/java/com/sistema_contabilidade/auth/config/AuthProviderProperties.java`
- `src/main/java/com/sistema_contabilidade/auth/config/CognitoProperties.java`
- `src/main/java/com/sistema_contabilidade/auth/service/AuthProviderStrategyResolver.java`
- `src/main/java/com/sistema_contabilidade/auth/service/LocalAuthProviderStrategy.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoAuthProviderStrategy.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoIdentitySyncService.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoRoleSyncService.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoUserManagementService.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoSecretHashService.java`
- `src/main/java/com/sistema_contabilidade/auth/service/LoginChallengeCookieService.java`
- `src/main/java/com/sistema_contabilidade/rbac/controller/AdminController.java`
- `src/main/java/com/sistema_contabilidade/rbac/service/RoleService.java`
- `src/main/java/com/sistema_contabilidade/auth/service/CognitoGroupCatalogService.java`

## UI and Pages

- `src/main/java/com/sistema_contabilidade/usuario/controller/PaginaUsuarioController.java`
- `src/main/resources/templates/home.html`
- `src/main/resources/templates/adicionar_comprovante.html`
- `src/main/resources/templates/lista_comprovantes.html`
- `src/main/resources/templates/relatorios.html`
- `src/main/resources/templates/notificacoes.html`
- `src/main/resources/templates/admin.html`
- `src/main/resources/templates/fragments/navbar.html`
- `src/main/resources/static/partials/navbar.html`
- `src/main/resources/static/login.html`
- `src/main/resources/static/primeiro_acesso.html`
- `src/main/resources/static/adicionar_comprovante.html`
- `src/main/resources/static/assets/js/auth-session-20260502-startup-perf-1.js`
- `src/main/resources/static/assets/js/navbar-20260502-startup-perf-1.js`
- `src/main/resources/static/assets/css/navbar-20260420-navbar-notification-count-fix-3.css`
- `src/main/resources/static/assets/js/primeiro_acesso-20260605-cognito-new-password-page-1.js`
- `src/main/resources/static/assets/js/adicionar_comprovante-20260427-auth-ready-1.js`

## Items, Upload and Listing

- `src/main/java/com/sistema_contabilidade/item/controller/ItemController.java`
- `src/test/java/com/sistema_contabilidade/item/controller/ItemControllerWebMvcTest.java`
- `src/main/java/com/sistema_contabilidade/item/service/PdfUploadSecurityValidator.java`
- `src/main/java/com/sistema_contabilidade/item/service/ItemListService.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemListPageRepositoryImpl.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemListSpecifications.java`
- `src/main/java/com/sistema_contabilidade/item/repository/ItemRepository.java`
- `src/main/java/com/sistema_contabilidade/item/model/ItemListPageQuery.java`
- `src/main/java/com/sistema_contabilidade/item/service/ItemRazaoSocialSearchInitializer.java`
- `src/main/java/com/sistema_contabilidade/item/service/ItemRazaoSocialSearchDatabaseSupport.java`
- `src/main/java/com/sistema_contabilidade/item/service/SearchTextNormalizer.java`

## Reports and Notifications

- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroService.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroConsolidador.java`
- `src/main/java/com/sistema_contabilidade/relatorio/repository/RelatorioResumoCategoriaRow.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/RelatorioFinanceiroPdfDataFactory.java`
- `src/main/java/com/sistema_contabilidade/relatorio/service/PlaywrightPdfService.java`
- `src/main/resources/templates/relatorio-financeiro.html`
- `src/main/resources/templates/relatorio-executivo-exemplo.html`

## Observability

- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountFilter.java`
- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountContext.java`
- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountStatementInspector.java`
- `src/main/java/com/sistema_contabilidade/monitoring/http/RequestTimingFilter.java`
- `src/main/java/com/sistema_contabilidade/monitoring/RequestMonitoringPathUtils.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/service/MemoryMonitoringService.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringMetrics.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringProperties.java`
- `src/test/java/com/sistema_contabilidade/monitoring/query/QueryCountAuditIntegrationTest.java`
- `src/test/java/com/sistema_contabilidade/monitoring/query/QueryCountPrometheusIntegrationTest.java`
- `observability/README.md`
- `observability/docker-compose.yml`
- Grafana/Prometheus provisioning files under `observability/`

## Config and Deployment

- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`
- `src/main/resources/application-prod.properties`
- `docker-compose.yml`
- `.env`
