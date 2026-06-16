---
name: sistema-contabilidade-project-guide
description: Router skill for the sistema_contabilidade repository. Use first before making changes to identify the module, choose the correct specialized skill, avoid repeated discovery, and preserve Spring Boot architecture, build, quality, security and deployment conventions.
---

# Sistema Contabilidade Project Guide

Use this skill first in the `sistema_contabilidade` repository.

This is a router skill. Its job is to choose the right specialized skill, not to load all project details.

## Core Workflow

1. Read `references/project-map.md`.
2. Identify the area touched by the task.
3. Open exactly one specialized skill first, unless the task clearly spans multiple areas.
4. Inspect the concrete local files listed by that specialized skill.
5. Preserve `controller -> service -> repository`.
6. Before Maven in PowerShell, run `.\scripts\use-java25.ps1`.
7. Prefer `.\mvnw` or `./mvnw`.
8. Keep Spotless, tests, Checkstyle, SpotBugs, PMD, Error Prone, ArchUnit, JaCoCo and Sonar clean.
9. If docs disagree with the current code, trust the code and note the divergence.
10. Update the relevant skill reference only when the change creates stable knowledge useful in future sessions.

## Route to Specialized Skills

| Task mentions | Read this skill |
|---|---|
| login, logout, refresh, session, cookie, Cognito, role, permission, admin, CORS, CSRF, security headers, JWT | `skills/sistema-contabilidade-auth-security/SKILL.md` |
| Thymeleaf, static page, navbar, frontend asset, JS, CSS, page flow, browser route, template | `skills/sistema-contabilidade-ui-pages/SKILL.md` |
| comprovante, item, upload, download, PDF attachment, listagem, filtro, paginacao, verificacao, observacao, razao social | `skills/sistema-contabilidade-items/SKILL.md` |
| relatorio, PDF financeiro, Playwright, notificacao, badge, receitas, despesas, resumo financeiro | `skills/sistema-contabilidade-reports-notifications/SKILL.md` |
| query count, N+1, performance, Prometheus, Grafana, Actuator, metrics, timing, memory, cache | `skills/sistema-contabilidade-observability/SKILL.md` |
| `.env`, profile, Docker, Redis, S3, CloudFront, ALB, DNS, certificate, production, CORS env | `skills/sistema-contabilidade-deploy-config/SKILL.md` |
| Maven, Java 25, tests, Sonar, quality gate, ArchUnit, Checkstyle, SpotBugs, PMD, dependency check | `skills/sistema-contabilidade-quality/SKILL.md` |
| issue plan, PRD to issues, roadmap, implementation tickets | `skills/to-issues/SKILL.md` |
| next session, context handoff, continue in another agent, summarize progress | `skills/productivity/handoff/SKILL.md` |

## Discovery Policy

Do not perform broad discovery by default.

Use targeted discovery:

- Read `references/project-map.md` for orientation.
- Read the specialized skill for the task.
- Read the exact files affected by the change.
- Read `references/file-lookup-index.md` only when you need a broader file list.

Broad discovery is allowed only when:

1. The task affects architecture across multiple modules.
2. The bug cannot be localized after targeted inspection.
3. The current docs are clearly outdated.
4. The user explicitly asks for full project analysis.

## Invariants

- Authenticated pages are primarily rendered by Thymeleaf templates.
- Public `/login` and `/primeiro_acesso` are served from `src/main/resources/static`.
- Production auth defaults to Cognito through `application-prod.properties`.
- Real session auth depends on `SC_SESSION`; `SC_TOKEN` is legacy compatibility.
- Hidden/admin routes are controlled by `AdminRouteService`.
- Static assets use filename-based versioning, not `?v=`.
- Never expose secrets from `.env`.
