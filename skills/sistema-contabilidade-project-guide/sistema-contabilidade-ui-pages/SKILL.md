---
name: sistema-contabilidade-ui-pages
description: Use for sistema_contabilidade Thymeleaf templates, static auth pages, navbar, frontend JS/CSS assets, route rendering, asset versioning, page flow and browser UX behavior.
---

# Sistema Contabilidade UI Pages

Use this skill when the task touches HTML pages, Thymeleaf templates, static auth pages, JS/CSS assets, navbar, page redirects, cards, filters or frontend behavior.

## First Read

1. Read `references/ui-pages.md`.
2. Identify whether the page is Thymeleaf, static HTML, or both.
3. Inspect the matching template, static fallback, JS and CSS.
4. If auth/session behavior is involved, also read `skills/sistema-contabilidade-auth-security/SKILL.md`.

## Main Files

- `src/main/java/com/sistema_contabilidade/usuario/controller/PaginaUsuarioController.java`
- `src/main/resources/templates/fragments/navbar.html`
- `src/main/resources/static/partials/navbar.html`
- `src/main/resources/static/assets/js/navbar-20260502-startup-perf-1.js`
- `src/main/resources/static/assets/css/navbar-20260420-navbar-notification-count-fix-3.css`
- `src/main/resources/static/assets/js/auth-session-20260502-startup-perf-1.js`
- `src/main/resources/static/primeiro_acesso.html`
- `src/main/resources/static/assets/js/primeiro_acesso-20260605-cognito-new-password-page-1.js`
- Page templates under `src/main/resources/templates/`
- Static fallbacks under `src/main/resources/static/`

## Workflow

1. Confirm the route in `PaginaUsuarioController`.
2. Determine if the current source of truth is a template, static file, or both.
3. Keep Thymeleaf navbar and static navbar synchronized.
4. Keep versioned asset filenames synchronized with template/static references.
5. Do not assume hardcoded select/card values; check page JS and backend-driven endpoints.
6. Validate browser behavior with auth/session bootstrap in mind.
7. If changing cache/compression strategy, inspect page heads, `SecurityConfig` and resource config.

## Validation

- Run the nearest WebMvc tests when routing or access changes.
- Run JS/CSS manual browser validation for layout/asset tasks.
- If auth-related, validate login, refresh and first render.
