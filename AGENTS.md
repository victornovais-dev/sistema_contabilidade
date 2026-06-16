# AGENTS.md

## Build Commands

- `./mvnw clean install`: full build
- `./mvnw verify`: run quality gates
- `./mvnw test`: run tests
- `./mvnw spotless:apply`: apply formatting
- `./mvnw checkstyle:check`: Checkstyle
- `./mvnw spotbugs:check`: SpotBugs
- `./mvnw pmd:check`: PMD
- `./mvnw -DskipTests compile`: static compilation / Error Prone
- `./mvnw -Dtest=ArchitectureRulesTest test`: ArchUnit
- `./mvnw dependency-check:check`: OWASP Dependency-Check

## PowerShell Java Setup

Before running Maven in PowerShell, load Java 25:

```powershell
.\scripts\use-java25.ps1
```

Prefer `./mvnw` or `.\mvnw` instead of global Maven.

## Quality Rules

- Use Google Java Style.
- Keep Spotless, Checkstyle, SpotBugs, PMD, Error Prone, ArchUnit, JaCoCo and Sonar clean.
- Keep tests green before commit.
- Keep commits focused by feature/package.
- Preserve the enforced layering: `controller -> service -> repository`.

## Security Rules

- Never commit secrets.
- Never print tokens/passwords from `.env`; mask values when inspecting them.
- Use environment variables for credentials and keys:
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `SESSION_CRYPTO_SECRET`
  - `JWT_EC_PRIVATE_KEY`
  - `JWT_EC_PUBLIC_KEY`
  - `AWS_REGION`
  - `COGNITO_USER_POOL_ID`
  - `COGNITO_APP_CLIENT_ID`
  - `APP_CORS_ALLOWED_ORIGINS`

## Skill Loading Rules

Always look for local skills inside:

```text
skills/
```

At the start of work in this repository, read:

```text
skills/sistema-contabilidade-project-guide/SKILL.md
```

Do not paste large project context into the working prompt. Use the project guide to choose one specialized skill and read only the needed references.

## Skill Routing

Use these local skills:

| Situation | Skill |
|---|---|
| General project orientation, module choice, avoiding rediscovery | `skills/sistema-contabilidade-project-guide/SKILL.md` |
| Login, logout, refresh, Cognito, roles, admin routes, CORS, CSRF, session cookies | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-auth-security/SKILL.md` |
| Thymeleaf pages, static HTML, navbar, CSS/JS assets, frontend route behavior | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-ui-pages/SKILL.md` |
| Comprovantes, item list, upload/download, PDF validation, verification rules | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-items/SKILL.md` |
| Financial reports, PDF report generation, notifications, navbar badge | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-reports-notifications/SKILL.md` |
| Query count, N+1, Prometheus, Grafana, Actuator, timing, memory, cache | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-observability/SKILL.md` |
| `.env`, profiles, Docker, Redis, CloudFront, ALB, DNS, S3, production config | `skills/sistema-contabilidade-project-guide/sistema-contabilidade-deploy-config/SKILL.md` |
| Maven quality workflow, tests, SonarQube, ArchUnit, precommit checks | `skills/sistema-contabilidade-quality/SKILL.md` |
| Convert plan/PRD/spec into implementation issues | `skills/to-issues/SKILL.md` |
| Prepare context for another session or agent | `skills/handoff/SKILL.md` |

## Debugging Skills

For debugging or larger refactors, also use these local skills when they exist:

- `skills/codex-debug/SKILL.md`
- `skills/pragmatic-programmer/SKILL.md`
- `skills/refactoring-patterns/SKILL.md`
- `skills/cyclomatic-complexity-spring/SKILL.md`
- `skills/spring-query-monitor/SKILL.md`

## Caveman Mode

- At the start of every Codex session in this repository, activate:
  - `$caveman full`
- Keep Caveman active for all normal agent responses.
- Use concise, compressed output.
- Remove filler, pleasantries, repeated explanations, and unnecessary summaries.
- Preserve technical accuracy, exact error messages, file paths, commands, code, class names, method names, and configuration names.
- Do not compress code blocks.
- Do not compress commit messages, PR descriptions, issue bodies, documentation meant for users, or generated project files unless the user explicitly asks.
- For destructive actions, security warnings, secrets, credentials, production deploys, migrations, or data deletion, use normal clear language first, then return to Caveman mode.
- If the user says `normal mode`, `stop caveman`, or asks for detailed explanation, stop using Caveman for that response.

## Commit Flow

Before every commit:

1. `./mvnw spotless:apply`
2. `./mvnw test`
3. `./mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check`
4. `./mvnw verify`
