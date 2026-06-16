# UI Pages Reference

## Rendering Model

- Authenticated pages are primarily rendered from `src/main/resources/templates`.
- Static files under `src/main/resources/static` still drive public auth pages, assets and fallback flows.
- Public `/login` and `/primeiro_acesso` are served directly from `static`.
- `/` is auth-aware:
  - anonymous -> `/login`
  - authenticated -> `/home`

## Routes

- `/login`
- `/primeiro_acesso`
- `/criar_usuario`
- `/atualizar_usuario`
- `/adicionar_comprovante`
- `/home`
- `/lista_comprovantes`
- `/relatorios`
- `/notificacoes`
- `/admin`
- `/gerenciar_roles`
- `/404`

## Navbar and Shared Frontend

Keep these synchronized:

- `src/main/resources/templates/fragments/navbar.html`
- `src/main/resources/static/partials/navbar.html`
- `static/assets/js/navbar-20260502-startup-perf-1.js`
- `static/assets/css/navbar-20260420-navbar-notification-count-fix-3.css`

Rules:

- `auth-session.js` centralizes bootstrap, refresh and logout.
- `auth-session.js` also centralizes shared role cache through `SCAuth.getUserRoles()`.
- `GET /api/v1/auth/routes` is restricted to admin; `403` for non-admin can be frontend noise.
- Keep `auth/routes` and navbar helper calls out of the critical first-render path when possible.
- Notification badge counts only unchecked notifications.

## Assets

- Main frontend assets use filename versioning:
  - `auth-session-20260502-startup-perf-1.js`
  - `navbar-20260420-navbar-notification-count-fix-3.css`
  - `lista_comprovantes-20260513-descender-fix-1.css`
- Do not revert to `?v=` query params unless explicitly intended.
- Static asset caching/compression can be affected by CDN/proxy layers.
