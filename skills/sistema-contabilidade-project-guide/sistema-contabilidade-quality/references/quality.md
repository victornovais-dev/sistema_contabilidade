# Quality Reference

## Build

- Java 25.
- Maven Wrapper.
- Spotless.
- Checkstyle.
- SpotBugs.
- PMD.
- Error Prone through static compilation.
- JaCoCo.
- ArchUnit.
- OWASP Dependency-Check.

## Architecture

ArchUnit expects:

- `*Controller` in `..controller..`
- `*Service` in `..service..`
- `*Repository` in `..repository..`

Preferred flow:

```text
controller -> service -> repository
```

## Standard Flow

```powershell
.\scripts\use-java25.ps1
.\mvnw spotless:apply
.\mvnw test
.\mvnw -DskipTests compile checkstyle:check spotbugs:check pmd:check
.\mvnw verify
```

## SonarQube

Utility script:

- `scripts/sonar-precommit.ps1`

Environment variables:

- `SONAR_HOST_URL`
- `SONAR_TOKEN` or `SONARQUBE_TOKEN`
- `SONAR_PROJECT_KEY`

- Project metrics require `SONAR_TOKEN` or `SONARQUBE_TOKEN`; query the configured instance instead of recording volatile snapshots.

## Tests

Dedicated test areas include:

- auth/session/csrf
- WebMvc controllers
- local and S3 storage
- Playwright PDF
- notifications
- server-side item pagination
- optimistic locking and legacy item verification
- memory/request timing monitoring
- query count audit
- Prometheus
- ArchUnit

Before altering sensitive rules, locate the closest test first and align changes there.
