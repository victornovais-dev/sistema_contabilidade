## Build Commands
- `./mvnw clean install`: full build
- `./mvnw verify`: run quality gates
- `./mvnw test`: run tests
- `./mvnw spotless:apply`: apply formatting
- `./mvnw checkstyle:check`: Checkstyle (Google Java Style)
- `./mvnw spotbugs:check`: SpotBugs
- `./mvnw pmd:check`: PMD
- `./mvnw -DskipTests compile`: Error Prone (compilação estática)
- `./mvnw -Dtest=ArchitectureRulesTest test`: ArchUnit
- `./mvnw dependency-check:check`: OWASP Dependency-Check

## Quality Rules
- Use Google Java Style
- Keep Spotless, Checkstyle, SpotBugs, PMD, Error Prone and ArchUnit passing
- Keep tests green before commit

## Security Rules
- Never commit secrets
- Use environment variables for credentials and keys:
  - `DB_USERNAME`
  - `DB_PASSWORD`
  - `SESSION_CRYPTO_SECRET`
  - `JWT_EC_PRIVATE_KEY`
  - `JWT_EC_PUBLIC_KEY`

## Commit Flow
Before every commit:
1. `./mvnw spotless:apply`
2. `./mvnw test`
3. `./mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check`
4. `./mvnw verify`

## Notes
- Prefer `./mvnw` instead of global Maven
- Keep commits focused by feature/package
- Always read the local skill `sistema-contabilidade-project-guide` before starting work in this repository:
  - `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\skills\sistema-contabilidade-project-guide\SKILL.md`
- When the user asks to use a skill, always look for it first in the subfolders inside the local project skills directory:
  - `A:\Projetos IA\Sistema\projeto\sistema_contabilidade\skills`
- For code debugging tasks, use the local skills `codex-debug` and `pragmatic-programmer` and `refactoring-patterns`
- Before running Maven in PowerShell, load Java 25 with:
  - `.\scripts\use-java25.ps1`

# Skill: to-issues

Use the `to-issues` skill when the user asks to:

- Convert a plan into issues.
- Convert a PRD into issues.
- Convert a specification into implementation tickets.
- Break a feature into GitHub issues.
- Prepare work for another coding agent.
- Split a large task into smaller deliverable slices.
- Create a roadmap of implementation issues.

Local expected path:

```text
A:\Projetos IA\Sistema\projeto\sistema_contabilidade\skills\to-issues
```
## to-issues Process

When using `to-issues`, the agent must:

1. Read the local `to-issues/SKILL.md`.
2. Understand the requested feature, plan, PRD, or task.
3. Inspect the codebase when needed.
4. Identify existing architecture, packages, conventions, tests, and domain vocabulary.
5. Break the work into thin vertical slices.
6. Prefer tracer-bullet issues that deliver end-to-end behavior.
7. Avoid purely horizontal issues such as only "create repository", only "create DTO", or only "create controller", unless they are unavoidable.
8. Classify each issue as:

```text
AFK
HITL
```

Use:

- `AFK`: the task can be implemented without human interaction.
- `HITL`: the task requires human approval, product decision, design choice, credential, external access, or business clarification.

9. Identify dependencies between issues.
10. Present the issue plan before publishing or creating issues.
11. Ask for approval before creating real issues.
12. Publish or create issues only after approval.
13. Create issues in dependency order.

## to-issues Proposal Format

Use this format when proposing issues:

```md
1. Title: <short issue title>
   Type: AFK | HITL
   Blocked by: <None or dependency>
   User stories covered: <story reference or Not specified>
```

## to-issues Issue Body Template

Use this template when writing the body of each issue:

```md
## Parent

<Reference to the parent issue, PRD, plan, document, or conversation. Omit if not applicable.>

## What to build

<Describe the vertical slice and the end-to-end behavior expected.>

Avoid specific file paths or code snippets unless they are necessary to preserve an important decision.

## Acceptance criteria

- [ ] <Criterion 1>
- [ ] <Criterion 2>
- [ ] <Criterion 3>

## Blocked by

<Blocking issue reference, or "None - can start immediately">
```

---
# Skill: handoff

Use the `handoff` skill when the user asks to:

- Continue the work in another session.
- Prepare context for another agent.
- Create a handoff document.
- Summarize the current state of the project.
- Summarize a debugging session.
- Summarize implementation progress.
- Preserve context before switching agents or conversations.
- Generate a document for the next Codex session.

Local expected path:

```text
A:\Projetos IA\Sistema\projeto\sistema_contabilidade\skills\productivity\handoff\SKILL.md
```

## handoff Process

When using `handoff`, the agent must:

1. Read the local `handoff/SKILL.md`.
2. Summarize only relevant context.
3. Include what was done, what remains, and what the next agent should do.
4. Reference existing artifacts instead of duplicating them.
5. Include suggested skills for the next agent.
6. Remove secrets and sensitive information.
7. Save the handoff document in the OS temporary directory, not in the current workspace.
8. Tell the user where the handoff was saved.

## handoff Document Template

Use this structure:

```md
# Handoff

## Purpose of next session

<What the next agent or next session should focus on.>

## Current status

<What has already been done.>

## Important context

<Project rules, architecture notes, constraints, decisions, and known trade-offs.>

## Relevant artifacts

- <Path, URL, issue, PRD, ADR, commit, diff, or file reference>

## Suggested skills

- `<skill-name>`: <why this skill should be used>

## Open questions

- <Question 1>
- <Question 2>

## Next recommended actions

1. <Action 1>
2. <Action 2>
3. <Action 3>

## Safety and security notes

<Confirm that secrets and sensitive data were redacted. Mention anything the next agent must avoid exposing.>
```

---
