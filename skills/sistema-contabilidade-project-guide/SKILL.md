---
name: sistema-contabilidade-project-guide
description: Guide for understanding the sistema_contabilidade repository before making changes. Use when Codex needs a fast project map, module overview, page flow, build rules, local profile behavior, storage/cache/security conventions, or architecture constraints for this Spring Boot accounting system.
---

# Sistema Contabilidade Project Guide

Use this skill at the start of work in this repository to avoid rediscovering the same context.

## Core Workflow

1. Read [references/project-analysis.md](references/project-analysis.md) first.
2. Confirm which module the task touches: `auth`, `usuario`, `item`, `home`, `relatorio`, `notificacao`, `security`, `rbac`, or static pages.
3. Preserve the enforced layering: `controller -> service -> repository`.
4. Before Maven in PowerShell, load Java 25 with `.\scripts\use-java25.ps1`.
5. Prefer `.\mvnw` and keep Spotless, tests, Checkstyle, SpotBugs, PMD, Error Prone, and ArchUnit green.

## Working Rules

- Treat `src/main/resources/static` as the current UI source of truth for the served pages.
- Expect backend-driven selects and cards in several static pages; check the page JS before assuming hardcoded options.
- For debugging or larger changes, also use the local skills `codex-debug`, `pragmatic-programmer`, and `refactoring-patterns`.
- Keep secrets out of commits; the project imports `.env`, so environment overrides can silently change local behavior.

## Load More Context Only When Needed

- Read `src/test/java/com/sistema_contabilidade/architecture/ArchitectureRulesTest.java` when a change touches package boundaries or naming conventions.
- Read `src/main/java/com/sistema_contabilidade/security/config/SecurityConfig.java` when the task affects auth, static page access, API access, CORS, or CSRF.
- Read `src/main/java/com/sistema_contabilidade/usuario/controller/PaginaUsuarioController.java` when adding or changing static pages.
- Read `src/main/resources/application.properties` and `src/main/resources/application-local.properties` when behavior differs by environment, storage, cache, or Redis.
