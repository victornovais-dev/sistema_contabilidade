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
