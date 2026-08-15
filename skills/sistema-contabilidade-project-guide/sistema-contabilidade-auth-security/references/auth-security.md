# Auth and Security Reference

## Key Rules

- Production auth defaults to Cognito in `application-prod.properties`.
- `app.auth.provider` is resolved from `APP_AUTH_PROVIDER`.
- `AuthService` delegates to `AuthProviderStrategyResolver`.
- Provider implementations include `LocalAuthProviderStrategy` and `CognitoAuthProviderStrategy`.
- Real auth depends on `SC_SESSION`.
- `SC_TOKEN` is legacy compatibility, not the main session path.
- First-access Cognito challenge uses `SC_LOGIN_CHALLENGE`.
- Login may return `202 Accepted` with challenge metadata before a normal session cookie exists.
- The admin update-by-email endpoint uses `email` as the current identity and accepts optional
  `novoEmail` and `nome`; omit either to preserve the current value. When
  `forcarTrocaSenha=true`, require a nonblank temporary `senha`: Cognito uses
  `AdminSetUserPassword` with `permanent=false`, while the local provider persists
  `usuarios.troca_senha_obrigatoria=true`. Both paths return `NEW_PASSWORD_REQUIRED` at the
  next login; completing the new password clears the local flag. The column is created by
  `V9__add_required_password_change.sql`.
- Public `/login` and `/primeiro_acesso` are static pages.
- Admin routes are hidden behind `AdminRouteService`.
- Legacy `/admin`-style URLs may intentionally redirect to `/404`.
- `PasswordEncoder` is Argon2 with compatibility for old hashes.
- `APP_CORS_ALLOWED_ORIGINS` must include production domains or login/refresh can fail with `Invalid CORS request`.

## Cognito Required Prod Config

When `app.auth.provider=cognito` in `prod`, startup validates:

- `AWS_REGION`
- `COGNITO_USER_POOL_ID`
- `COGNITO_APP_CLIENT_ID`
- `SESSION_CRYPTO_SECRET`
- `JWT_EC_PRIVATE_KEY`
- `JWT_EC_PUBLIC_KEY`

`COGNITO_APP_CLIENT_SECRET` is optional. If configured, `CognitoSecretHashService` adds `SECRET_HASH`.

## Cognito IAM / App Client

Production login/admin role catalog may require:

- `AdminInitiateAuth`
- `AdminRespondToAuthChallenge`
- `AdminGetUser`
- `AdminListGroupsForUser`
- `AdminSetUserPassword`
- `AdminUpdateUserAttributes`
- `InitiateAuth`
- `ListGroups`

## Roles and Cache

- `CognitoRoleSyncService` creates missing local roles from normalized Cognito group names.
- Candidate Cognito groups use description `candidato`, optionally followed by `;dominio=<domain>`.
  Domain-tagged groups appear and can be assigned only by `CONTABIL` users whose authenticated
  email has that domain; `ADMIN` bypasses this filter.
- `ESTAGIARIO` is a technical role with the same item and notification permissions as `CONTABIL`.
- `CONTABIL` may manage only campaign roles of users whose sole technical role is `ESTAGIARIO`, using `/api/v1/estagiarios`; the server always preserves `ESTAGIARIO` and rejects technical roles such as `CANDIDATO`, `CONTABIL` and `ADMIN`.
- Admin user screens should use `GET /api/v1/admin/roles/disponiveis`.
- `CustomUserDetailsService` warms/evicts `userDetails` cache entries for both email and `id:<uuid>`.
- Already logged-in users may still need fresh login/session refresh after permission changes.
- SpEL cache keys on proxied methods should prefer positional parameters such as `#p0.email`.

## Known Prod Data Issues

Before blaming Cognito, inspect DB/schema for:

- `usuarios.version = null`
- zero dates in `sessoes_usuario.atualizada_em`
- undersized encrypted refresh/session snapshot columns such as `refresh_token_cifrado` and `groups_snapshot`
