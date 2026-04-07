# Testes — JaCoCo, Security Headers e Configuração Completa

---

## 1. Configuração JaCoCo

```xml
<!-- pom.xml — plugin completo do JaCoCo -->
<plugin>
    <groupId>org.jacoco</groupId>
    <artifactId>jacoco-maven-plugin</artifactId>
    <version>0.8.12</version>
    <executions>

        <!-- Inicializar agente JaCoCo antes dos testes -->
        <execution>
            <id>prepare-agent</id>
            <goals><goal>prepare-agent</goal></goals>
        </execution>

        <!-- Inicializar agente para testes de integração -->
        <execution>
            <id>prepare-agent-integration</id>
            <goals><goal>prepare-agent-integration</goal></goals>
        </execution>

        <!-- Gerar relatório após unit tests -->
        <execution>
            <id>report</id>
            <phase>test</phase>
            <goals><goal>report</goal></goals>
        </execution>

        <!-- Gerar relatório após integration tests -->
        <execution>
            <id>report-integration</id>
            <phase>verify</phase>
            <goals><goal>report-integration</goal></goals>
        </execution>

        <!-- Verificar thresholds de cobertura — falha o build se não atingir -->
        <execution>
            <id>check</id>
            <phase>verify</phase>
            <goals><goal>check</goal></goals>
            <configuration>
                <rules>
                    <!-- Regra global -->
                    <rule>
                        <element>BUNDLE</element>
                        <limits>
                            <limit>
                                <counter>INSTRUCTION</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.80</minimum> <!-- 80% instrução global -->
                            </limit>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.75</minimum> <!-- 75% branch global -->
                            </limit>
                        </limits>
                    </rule>

                    <!-- Regra por pacote de segurança — threshold mais alto -->
                    <rule>
                        <element>PACKAGE</element>
                        <includes>
                            <include>com/suaempresa/app/security/*</include>
                            <include>com/suaempresa/app/filter/*</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum> <!-- 90% para código de segurança -->
                            </limit>
                        </limits>
                    </rule>

                    <!-- Regra por classe de serviço de auth -->
                    <rule>
                        <element>CLASS</element>
                        <includes>
                            <include>com/suaempresa/app/service/AuthService</include>
                            <include>com/suaempresa/app/security/JwtService</include>
                        </includes>
                        <limits>
                            <limit>
                                <counter>BRANCH</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>0.90</minimum>
                            </limit>
                            <limit>
                                <counter>METHOD</counter>
                                <value>COVEREDRATIO</value>
                                <minimum>1.00</minimum> <!-- 100% dos métodos de auth -->
                            </limit>
                        </limits>
                    </rule>
                </rules>

                <!-- Excluir classes que não fazem sentido cobrir -->
                <excludes>
                    <exclude>**/dto/**</exclude>         <!-- DTOs são POJOs -->
                    <exclude>**/entity/**</exclude>       <!-- Entities são POJOs -->
                    <exclude>**/config/**</exclude>       <!-- Config beans -->
                    <exclude>**/*Application.class</exclude> <!-- Main class -->
                    <exclude>**/generated/**</exclude>    <!-- Código gerado -->
                </excludes>
            </configuration>
        </execution>

        <!-- Merge de relatórios unit + integration -->
        <execution>
            <id>merge-results</id>
            <phase>verify</phase>
            <goals><goal>merge</goal></goals>
            <configuration>
                <fileSets>
                    <fileSet>
                        <directory>${project.build.directory}</directory>
                        <includes>
                            <include>jacoco.exec</include>
                            <include>jacoco-it.exec</include>
                        </includes>
                    </fileSet>
                </fileSets>
                <destFile>${project.build.directory}/jacoco-merged.exec</destFile>
            </configuration>
        </execution>

        <!-- Relatório HTML do merge -->
        <execution>
            <id>report-merged</id>
            <phase>verify</phase>
            <goals><goal>report</goal></goals>
            <configuration>
                <dataFile>${project.build.directory}/jacoco-merged.exec</dataFile>
                <outputDirectory>${project.reporting.outputDirectory}/jacoco-merged</outputDirectory>
            </configuration>
        </execution>

    </executions>
</plugin>
```

---

## 2. Profiles de Teste

```yaml
# src/test/resources/application-test.yml
spring:
  # Flyway executa migrations de teste
  flyway:
    locations: classpath:db/migration,classpath:db/testdata
    clean-on-validation-error: true

  # JPA — schema auto para testes
  jpa:
    hibernate:
      ddl-auto: validate # ou create-drop para desenvolvimento
    show-sql: true
    properties:
      hibernate:
        format_sql: true

  # Redis embed para testes (ou Testcontainers Redis)
  data:
    redis:
      host: localhost
      port: 6370 # porta diferente para não conflitar com Redis local

# Configurações de segurança para testes — valores dummy seguros
security:
  argon2:
    memory-cost: 1024   # reduzir para testes mais rápidos (não usar em produção!)
    time-cost: 1
    parallelism: 1

jwt:
  secret: dGVzdC1zZWNyZXQta2V5LXBhcmEtdGVzdGVzLW5hby11c2FyLWVtLXByb2R1Y2FvLWFxdWk=

admin:
  route:
    secret: test-admin-secret-nao-usar-em-producao

upload:
  base-path: /tmp/test-uploads
  max-size-bytes: 10485760

app:
  base-url: http://localhost:8080
  allowed-origins: http://localhost:3000,http://localhost:4200

# Logging para testes
logging:
  level:
    com.suaempresa.app: DEBUG
    org.springframework.security: DEBUG
    org.springframework.test.web: DEBUG
```

---

## 3. Dados de Teste (Flyway)

```sql
-- src/test/resources/db/testdata/V999__test_data.sql
-- Dados mínimos para testes de segurança

-- Usuário comum para testes gerais
INSERT INTO users (id, email, password_hash, role, active, created_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440001',
    'user@test.com',
    '$argon2id$v=19$m=1024,t=1,p=1$dGVzdHNhbHQ$test-hash-placeholder', -- será substituído
    'USER',
    true,
    NOW()
);

-- Usuário admin para testes de admin
INSERT INTO users (id, email, password_hash, role, active, created_at)
VALUES (
    '550e8400-e29b-41d4-a716-446655440002',
    'admin@test.com',
    '$argon2id$v=19$m=1024,t=1,p=1$dGVzdHNhbHQ$test-hash-placeholder',
    'ADMIN',
    true,
    NOW()
);
```

---

## 4. Testes de Security Headers

```java
// test/.../headers/SecurityHeadersTest.java
@DisplayName("Security Headers — Presença e Valores Corretos")
class SecurityHeadersTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("Todos os security headers obrigatórios devem estar presentes")
    void dado_qualquerResponse_entao_headersDeSegurancaPresentes() throws Exception {
        mockMvc.perform(get("/api/posts"))
            // Anti-clickjacking
            .andExpect(header().string("X-Frame-Options", "DENY"))
            // Prevenir MIME sniffing
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            // Desabilitar XSS auditor antigo (causa bypasses)
            .andExpect(header().string("X-XSS-Protection", "0"))
            // Referrer policy
            .andExpect(header().string("Referrer-Policy", "strict-origin-when-cross-origin"))
            // Permissions policy
            .andExpect(header().exists("Permissions-Policy"))
            // CSP
            .andExpect(header().exists("Content-Security-Policy"));
    }

    @Test
    @DisplayName("Header Server não deve revelar tecnologia")
    void dado_qualquerResponse_entao_semHeaderServer() throws Exception {
        mockMvc.perform(get("/api/posts"))
            .andExpect(header().doesNotExist("Server"))
            .andExpect(header().doesNotExist("X-Powered-By"))
            .andExpect(header().doesNotExist("X-Application-Context"));
    }

    @Test
    @DisplayName("HSTS deve estar configurado com includeSubDomains")
    void dado_requestHttps_entao_hstsPresente() throws Exception {
        // Simular request HTTPS
        mockMvc.perform(get("/api/posts")
                .secure(true))
            .andExpect(header().exists("Strict-Transport-Security"))
            .andExpect(header().string("Strict-Transport-Security",
                allOf(
                    containsString("max-age=31536000"),
                    containsString("includeSubDomains")
                )));
    }

    @Test
    @DisplayName("CSP deve ter nonce e não ter unsafe-inline para scripts")
    void dado_qualquerResponse_entao_cspComNonceSemUnsafeInline() throws Exception {
        var result = mockMvc.perform(get("/api/posts"))
            .andReturn();

        String csp = result.getResponse().getHeader("Content-Security-Policy");
        assertThat(csp).isNotNull();
        assertThat(csp).contains("nonce-");           // nonce presente
        assertThat(csp).doesNotContain("'unsafe-inline'"); // sem unsafe-inline
        assertThat(csp).contains("frame-ancestors 'none'"); // anti-clickjacking via CSP
        assertThat(csp).contains("object-src 'none'"); // sem Flash/plugins
        assertThat(csp).contains("base-uri 'self'");   // anti base tag injection

        // Nonce deve ser diferente a cada request
        var result2 = mockMvc.perform(get("/api/posts")).andReturn();
        String csp2 = result2.getResponse().getHeader("Content-Security-Policy");

        String nonce1 = extractNonce(csp);
        String nonce2 = extractNonce(csp2);
        assertThat(nonce1).isNotEqualTo(nonce2); // nonces únicos por request
    }

    @Test
    @DisplayName("Respostas autenticadas não devem ser cacheadas")
    void dado_responseAutenticada_entao_semCache() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(header().string("Cache-Control",
                allOf(
                    containsString("no-store"),
                    containsString("no-cache")
                )));
    }

    private String extractNonce(String csp) {
        // Extrair valor do nonce do CSP header
        java.util.regex.Matcher m =
            java.util.regex.Pattern.compile("'nonce-([^']+)'").matcher(csp);
        return m.find() ? m.group(1) : "";
    }
}
```

---

## 5. Testes de Concorrência e Race Condition

```java
// test/.../concurrency/RaceConditionTest.java
@DisplayName("Race Conditions — Operações Concorrentes")
class RaceConditionTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("Transferência concorrente não deve permitir saldo negativo")
    void dado_transferenciasSimultaneas_entao_saldoNuncaNegativo()
            throws InterruptedException, ExecutionException {

        // Setup: conta com saldo 100
        Account account = createAccountWithBalance("100.00");
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        int numThreads = 10;
        BigDecimal valorTransferencia = new BigDecimal("15.00");
        // 10 threads tentando transferir 15 cada = 150 total > 100 saldo

        ExecutorService executor = Executors.newFixedThreadPool(numThreads);
        CountDownLatch latch = new CountDownLatch(1);
        List<Future<Integer>> futures = new ArrayList<>();

        for (int i = 0; i < numThreads; i++) {
            futures.add(executor.submit(() -> {
                latch.await(); // todos partem ao mesmo tempo
                var result = mockMvc.perform(post("/api/accounts/transfer")
                        .header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                            "fromAccountId", account.getId(),
                            "toAccountId", UUID.randomUUID(),
                            "amount", valorTransferencia))))
                    .andReturn();
                return result.getResponse().getStatus();
            }));
        }

        latch.countDown(); // liberar todas as threads simultaneamente
        executor.shutdown();
        executor.awaitTermination(30, SECONDS);

        // Verificar que apenas algumas transferências foram aceitas
        long sucessos = futures.stream()
            .map(f -> { try { return f.get(); } catch (Exception e) { return 500; } })
            .filter(s -> s == 200)
            .count();

        // Com saldo de 100 e transferências de 15 — máximo 6 sucessos (6*15=90 <= 100)
        assertThat(sucessos).isLessThanOrEqualTo(6);

        // Saldo final nunca pode ser negativo
        Account updated = accountRepository.findById(account.getId()).orElseThrow();
        assertThat(updated.getBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Idempotency key previne processamento duplicado")
    void dado_mesmIdempotencyKey_entao_processadoUmaVez() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
        String idempotencyKey = UUID.randomUUID().toString();

        // Mesma request com mesmo idempotency key — duas vezes
        var result1 = mockMvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(APPLICATION_JSON)
                .content("{\"amount\": 50.00, \"description\": \"teste\"}"))
            .andReturn();

        var result2 = mockMvc.perform(post("/api/transactions")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey) // mesma key!
                .contentType(APPLICATION_JSON)
                .content("{\"amount\": 50.00, \"description\": \"teste\"}"))
            .andReturn();

        // Ambas retornam sucesso (idempotente)
        assertThat(result1.getResponse().getStatus()).isEqualTo(200);
        assertThat(result2.getResponse().getStatus()).isEqualTo(200);

        // Mas o resultado deve ser idêntico — processado apenas uma vez
        assertThat(result1.getResponse().getContentAsString())
            .isEqualTo(result2.getResponse().getContentAsString());

        // Verificar no banco: apenas uma transação criada
        long count = transactionRepository.countByIdempotencyKey(idempotencyKey);
        assertThat(count).isEqualTo(1);
    }
}
```

---

## 6. Executar Testes e Ver Cobertura

```bash
# Rodar todos os testes de integração
mvn verify -P integration-tests

# Rodar apenas testes de segurança
mvn test -Dtest="*SecurityTest,*HoneypotTest,*CsrfTest,*SsrfTest"

# Gerar relatório JaCoCo e abrir no browser
mvn verify jacoco:report
open target/site/jacoco/index.html

# Ver relatório consolidado (unit + integration)
open target/site/jacoco-merged/index.html

# Checar thresholds sem rodar testes
mvn jacoco:check

# Rodar com profile de testes paralelos (mais rápido)
mvn verify -T 4 -P integration-tests

# Gerar badge de cobertura para README
# (após mvn verify, o arquivo jacoco.xml está em target/site/jacoco/)
```

---

## 7. Anotações Customizadas para Testes de Segurança

```java
// Anotação para marcar testes de segurança — facilita filtragem
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("security")
@Tag("integration")
public @interface SecurityTest { }

// Anotação para testes de penetração simulada
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.RUNTIME)
@Tag("pentest")
@Tag("security")
public @interface PentestSimulation {
    String attack() default "";   // nome do ataque (SQLi, XSS, SSRF...)
    String cve() default "";      // CVE relacionado, se houver
    String layer() default "";    // camada de segurança sendo testada
}

// Uso:
@PentestSimulation(attack = "UNION SELECT", layer = "Layer 4 - SQLi")
@Test
void dado_unionSelectInjection_entao_bloqueado() { ... }

// Rodar apenas testes de pentest:
// mvn test -Dgroups="pentest"
// Rodar apenas testes de segurança:
// mvn test -Dgroups="security"
```
