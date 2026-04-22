# Configuração de Ferramentas Maven/Spring Boot

## 1. PMD (recomendado para CC)

### pom.xml
```xml
<build>
  <plugins>
    <plugin>
      <groupId>org.apache.maven.plugins</groupId>
      <artifactId>maven-pmd-plugin</artifactId>
      <version>3.21.0</version>
      <configuration>
        <targetJdk>17</targetJdk>
        <failurePriority>2</failurePriority>
        <printFailingErrors>true</printFailingErrors>
        <rulesets>
          <ruleset>pmd-rules.xml</ruleset>
        </rulesets>
      </configuration>
      <executions>
        <execution>
          <phase>verify</phase>
          <goals>
            <goal>check</goal>
          </goals>
        </execution>
      </executions>
    </plugin>
  </plugins>
</build>
```

### pmd-rules.xml (na raiz do projeto)
```xml
<?xml version="1.0"?>
<ruleset name="Custom CC Rules"
  xmlns="http://pmd.sourceforge.net/ruleset/2.0.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xsi:schemaLocation="http://pmd.sourceforge.net/ruleset/2.0.0
    https://pmd.sourceforge.io/ruleset_2_0_0.xsd">

  <description>Regras de complexidade ciclomática</description>

  <rule ref="category/java/design.xml/CyclomaticComplexity">
    <properties>
      <!-- Threshold: falha se CC > 10 -->
      <property name="methodReportLevel" value="10"/>
      <property name="classReportLevel" value="80"/>
    </properties>
  </rule>

  <rule ref="category/java/design.xml/NPathComplexity">
    <properties>
      <property name="reportLevel" value="200"/>
    </properties>
  </rule>

  <rule ref="category/java/design.xml/CognitiveComplexity">
    <properties>
      <property name="reportLevel" value="15"/>
    </properties>
  </rule>
</ruleset>
```

### Comandos Maven
```bash
# Gerar relatório HTML (target/site/pmd.html)
mvn pmd:pmd

# Falhar o build se CC exceder threshold
mvn pmd:check

# Ver apenas violações no console
mvn pmd:pmd -Dpmd.printFailingErrors=true
```

---

## 2. SonarQube / SonarCloud

### pom.xml
```xml
<properties>
  <sonar.host.url>http://localhost:9000</sonar.host.url>
  <sonar.login>${env.SONAR_TOKEN}</sonar.login>
  <!-- Threshold de qualidade -->
  <sonar.qualitygate.wait>true</sonar.qualitygate.wait>
</properties>

<plugin>
  <groupId>org.sonarsource.scanner.maven</groupId>
  <artifactId>sonar-maven-plugin</artifactId>
  <version>3.10.0.2594</version>
</plugin>
```

### Quality Gate no SonarQube
Configure via UI: Administration → Quality Gates → Add Condition:
- Metric: **Cyclomatic Complexity**
- Operator: is greater than
- Value: **10** (por método)

### Rodar análise
```bash
mvn verify sonar:sonar \
  -Dsonar.projectKey=meu-projeto \
  -Dsonar.host.url=http://localhost:9000 \
  -Dsonar.login=$SONAR_TOKEN
```

---

## 3. Checkstyle (complementar)

### pom.xml
```xml
<plugin>
  <groupId>org.apache.maven.plugins</groupId>
  <artifactId>maven-checkstyle-plugin</artifactId>
  <version>3.3.1</version>
  <configuration>
    <configLocation>checkstyle.xml</configLocation>
    <failsOnError>true</failsOnError>
  </configuration>
</plugin>
```

### checkstyle.xml
```xml
<?xml version="1.0"?>
<!DOCTYPE module PUBLIC
  "-//Checkstyle//DTD Checkstyle Configuration 1.3//EN"
  "https://checkstyle.org/dtds/configuration_1_3.dtd">
<module name="Checker">
  <module name="TreeWalker">
    <module name="CyclomaticComplexity">
      <property name="max" value="10"/>
      <property name="switchBlockAsSingleDecisionPoint" value="true"/>
    </module>
  </module>
</module>
```

---

## 4. GitHub Actions CI/CD Gate

```yaml
# .github/workflows/quality.yml
name: Code Quality

on: [push, pull_request]

jobs:
  quality:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4

      - name: Set up JDK 17
        uses: actions/setup-java@v4
        with:
          java-version: '17'
          distribution: 'temurin'

      - name: Cache Maven
        uses: actions/cache@v3
        with:
          path: ~/.m2
          key: ${{ runner.os }}-m2-${{ hashFiles('**/pom.xml') }}

      - name: Analyze Cyclomatic Complexity
        run: mvn pmd:check --no-transfer-progress

      - name: Upload PMD Report
        if: always()
        uses: actions/upload-artifact@v3
        with:
          name: pmd-report
          path: target/site/pmd.html
```

---

## 5. Relatório Visual (Maven Site)

```bash
# Gerar site completo com todos os relatórios
mvn site

# Abre: target/site/index.html
# Relatórios disponíveis em: target/site/pmd.html
```

Para projeto multi-módulo Spring Boot:
```bash
mvn site site:stage -DstagingDirectory=/tmp/site
```
