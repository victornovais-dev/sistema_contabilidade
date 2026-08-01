# UI Pages Reference

## Rendering Model

- Authenticated pages are primarily rendered from `src/main/resources/templates`.
- Static files under `src/main/resources/static` still drive public auth pages, assets and fallback flows.
- Public `/login`, `/primeiro_acesso` and `/conheca` are served directly from `static`.
- `/` is auth-aware:
  - anonymous -> `/login`
  - authenticated -> `/home`

## Routes

- `/login`
- `/primeiro_acesso`
- `/conheca`
- `/duvidas` (`ADMIN` only)
- `/criar_usuario`
- `/atualizar_usuario`
- `/gerenciar_estagiarios` (`CONTABIL` only)
- `/adicionar_comprovante`
- `/home`
- `/lista_comprovantes`
- `/relatorios`
  - Keep the Thymeleaf page and static fallback synchronized. PDF saturation shows a countdown card
    from `Retry-After`; the client must not retry automatically.
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
- `templates/lista_comprovantes.html`
- `static/lista_comprovantes.html`
- `static/assets/js/lista_comprovantes-20260801-detail-on-demand-1.js`
- `static/assets/css/lista_comprovantes-20260513-descender-fix-1.css`

Rules:

- `auth-session.js` centralizes bootstrap, refresh and logout.
- `auth-session.js` also centralizes shared role cache through `SCAuth.getUserRoles()`.
- `GET /api/v1/auth/routes` is restricted to admin; `403` for non-admin can be frontend noise.
- Keep `auth/routes` and navbar helper calls out of the critical first-render path when possible.
- Notification badge counts only unchecked notifications.

## Assets

- `/conheca` uses versioned CSS/JS assets plus a local optimized MP4 under
  `static/assets/video`. Its public question form submits to `POST /api/v1/duvidas`
  with the CSRF token from `GET /api/v1/auth/csrf`.
- `/duvidas` lists public questions for `ADMIN`, supports search/status filters and
  updates the workflow status through `PATCH /api/v1/duvidas/{protocolo}/status`.
- The first-access page displays the default Cognito password criteria below the new-password field; unmet criteria are red and become green in real time. A green strength bar reflects the five criteria, and password confirmation reports mismatches in real time. Cognito remains authoritative for the configured user-pool policy.
- Main frontend assets use filename versioning:
  - `auth-session-20260502-startup-perf-1.js`
  - `navbar-20260420-navbar-notification-count-fix-3.css`
  - `lista_comprovantes-20260513-descender-fix-1.css`
- List templates and static fallback use `lista_comprovantes-20260801-detail-on-demand-1.js`.
  It keeps only page data in memory and fetches authorized item details when opening observation,
  payment or attachment actions.
- Do not revert to `?v=` query params unless explicitly intended.
- Static asset caching/compression can be affected by CDN/proxy layers.

## Payment Modal

- The list page keeps template and static fallback aligned.
- `AVISTA` hides the installment-count control; `PARCELADO` exposes 2-4 installments.
- Installment amount inputs remain editable in `PARCELADO` and the modal total follows checked installments.
- Values use a cent-based Brazilian currency mask and cap at R$ 5,000,000.00, including direct manual input.
- PDF thumbnails use `assets/img/pdf-thumbnail-20260723-1.png`; filename and pending-remove control appear on hover.
- An installment accepts multiple PDFs; remove actions are pending until the user clicks save.
- Every paid installment requires source-account selection: `CONTA DC`, `CONTA FEFC` or `CONTA FP`; the account control is the dark custom dropdown in both payment forms.
- A saved form with a checked installment or attached PDF locks the other form. After the current form is saved clean, the user can reopen the modal and change it.
- On an unsaved form change, clear payment checkboxes, accounts and PDFs from the draft; never present one payment as both immediate and installment payment.
- After a successful save, refresh the modal from the returned item, show its success card, and keep the payment modal open when that card closes.
