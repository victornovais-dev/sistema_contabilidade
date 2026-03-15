## Comandos de Build
- `mvn clean install`: Build completo
- `mvn verify`: Roda todos os checks de qualidade
- `mvn checkstyle:check`: Checkstyle (Google Java Style)
- `mvn spotbugs:check`: SpotBugs
- `mvn pmd:check`: PMD
- `mvn spotless:check`: Verifica formatação
- `mvn spotless:apply`: Aplica formatação automática
- `mvn dependency-check:check`: OWASP Dependency-Check

## Padrões de Qualidade
- Usar Google Java Style (Checkstyle configurado)
- Formatação automática via Spotless + Google Java Format
- Zero warnings tolerados no SpotBugs e PMD
- Score mínimo SonarQube: A em confiabilidade e segurança

## Fluxo de CI
Antes de qualquer commit: spotless:apply → checkstyle → spotbugs → pmd → tests