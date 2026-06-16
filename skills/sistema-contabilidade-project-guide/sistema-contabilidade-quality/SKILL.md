---
name: sistema-contabilidade-quality
description: Use for sistema_contabilidade Maven build, Java 25 setup, tests, quality gates, SonarQube, Spotless, Checkstyle, SpotBugs, PMD, Error Prone, ArchUnit, JaCoCo and dependency checks.
---

# Sistema Contabilidade Quality

Use this skill when the task touches build, tests, quality gates, SonarQube, ArchUnit, style, static analysis or precommit workflow.

## First Read

1. Read `references/quality.md`.
2. Identify whether the task needs focused tests or full verification.
3. Inspect build/test config only when the failure points there.

## Commands

Before Maven in PowerShell:

```powershell
.\scripts\use-java25.ps1
```

Common workflow:

```powershell
.\mvnw spotless:apply
.\mvnw test
.\mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check
.\mvnw verify
```

Other useful commands:

```powershell
.\mvnw clean install
.\mvnw -Dtest=ArchitectureRulesTest test
.\mvnw dependency-check:check
```

## Workflow

1. Run the smallest relevant test first.
2. Fix style/analysis failures without weakening rules.
3. For package/layer changes, run `ArchitectureRulesTest`.
4. For SonarQube work, inspect `scripts/sonar-precommit.ps1` and `sonar-project.properties`.
5. Keep secrets masked when scripts use `SONAR_TOKEN` or `SONARQUBE_TOKEN`.
6. Run full quality workflow before finalizing broad changes.

## Validation

- Focused test suite for the changed area.
- Full `.\mvnw verify` for larger changes.
- Sonar/precommit script only when requested or when quality gate context matters.
