---
name: sistema-contabilidade-auth-security
description: Use for sistema_contabilidade authentication, authorization, Cognito, session cookies, first access, admin hidden routes, CORS, CSRF, security headers, roles, permissions and auth cache bugs.
---

# Sistema Contabilidade Auth and Security

Use this skill when the task touches login, logout, refresh, session cookies, Cognito, local auth, roles, permissions, admin routes, CORS, CSRF or security headers.

## First Read

1. Read `references/auth-security.md`.
2. Then inspect only the files relevant to the task.
3. If the task is also frontend-related, read `skills/sistema-contabilidade-ui-pages/SKILL.md`.

## Main Files

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

## Workflow

### Login/session bug

1. Inspect `AuthController`.
2. Inspect provider strategy resolver and the selected provider strategy.
3. Inspect cookie/session handling.
4. Check `SC_SESSION` first; treat `SC_TOKEN` as legacy compatibility.
5. Check whether `NEW_PASSWORD_REQUIRED` returns `202 Accepted`.
6. For first access, inspect the `/primeiro_acesso` static page and `SC_LOGIN_CHALLENGE`.

### Cognito production bug

1. Check `application-prod.properties`.
2. Check required environment variables.
3. Check `AuthProviderStartupValidator`.
4. Check Cognito app client flows and IAM permissions.
5. Check data-health issues before blaming Cognito.

### Role/admin bug

1. Inspect `AdminRouteService` and `SecurityPaths`.
2. Inspect `RoleService` and `CognitoGroupCatalogService`.
3. Check `GET /api/v1/admin/roles/disponiveis`.
4. Check role sync and local role materialization.
5. Check user details cache invalidation by email and `id:<uuid>`.

## Validation

- Run focused auth/security tests first.
- Then run `.\mvnw test`.
- For security config changes, run the relevant WebMvc/security tests and `ArchitectureRulesTest`.
