---
name: security-tests
description: >
  Criar testes integrados de segurança para aplicações Java Spring Boot usando JUnit 5,
  Spring Security Test, MockMvc, Testcontainers, REST Assured e JaCoCo.
  Use esta skill SEMPRE que o usuário pedir testes para: autenticação JWT, rate limiting,
  honeypots, SQL injection, XSS, IDOR, CSRF, upload de arquivos, SSRF, path traversal,
  auditoria de login, renderização segura, ou qualquer camada da skill springboot-security.
  Também trigger para: "escrever testes de segurança", "testar minha camada de auth",
  "como testar rate limiting", "cobertura JaCoCo dos filtros de segurança",
  "testes para meu filtro", "testes integrados Spring Security", "MockMvc security",
  "Testcontainers MySQL", "testar upload de arquivo malicioso".
  Esta skill cobre todas as 13 camadas da skill springboot-security com testes
  de integração reais — não unitários isolados.
---

# Spring Boot Security Tests — Testes Integrados por Camada

> **Filosofia:** Um teste de segurança que não falha quando a defesa é removida
> não é um teste de segurança — é decoração. Cada teste aqui foi escrito
> pensando: "o que acontece se eu deletar o filtro/validação/check?"

## Índice

| Referência | Camadas Cobertas | Foco |
|------------|------------------|------|
| `tests-auth-jwt.md` | Layer 1 + 6 | JWT, Argon2, refresh token, admin invisível |
| `tests-ratelimit-honeypot.md` | Layer 2 + 3 + 7 | Rate limiting, lockout, honeypots backend/frontend |
| `tests-input-validation.md` | Layer 4 | SQLi, XSS, IDOR, mass assignment, CSRF |
| `tests-file-upload.md` | Layer 5 | Magic bytes, polyglot, ZIP bomb, path traversal |
| `tests-ssrf-traversal.md` | Layer 8 | SSRF, DNS rebinding, path traversal |
| `tests-audit-rendering.md` | Layer 10 + 11 | Logs de auditoria, PDF/Markdown, SSTI |
| `tests-database.md` | Layer 9 + 12 | CSRF, MySQL hardening, privilege escalation |
| `tests-jacoco-config.md` | Todas | Configuração JaCoCo, thresholds, relatórios |

---

## Guia de Decisão

```
Testar login/logout/JWT?              → tests-auth-jwt.md
Testar rate limiting/brute force?     → tests-ratelimit-honeypot.md
Testar input suspeito/SQLi/XSS?       → tests-input-validation.md
Testar upload de arquivo?             → tests-file-upload.md
Testar SSRF/URL externa?              → tests-ssrf-traversal.md
Testar logs de segurança?             → tests-audit-rendering.md
Testar CSRF/banco de dados?           → tests-database.md
Configurar JaCoCo/cobertura?          → tests-jacoco-config.md
Testes para um projeto do zero?       → ler TODOS os arquivos
```

---

## Dependências (pom.xml)

```xml
<dependencies>
    <!-- Spring Boot Test Starter (inclui JUnit 5 + Mockito + MockMvc) -->
    <dependency>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Spring Security Test (SecurityMockMvcRequestPostProcessors, WithMockUser etc.) -->
    <dependency>
        <groupId>org.springframework.security</groupId>
        <artifactId>spring-security-test</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Testcontainers — MySQL e Redis reais nos testes -->
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>junit-jupiter</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>org.testcontainers</groupId>
        <artifactId>mysql</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- REST Assured — testes HTTP de alto nível -->
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>rest-assured</artifactId>
        <scope>test</scope>
    </dependency>
    <dependency>
        <groupId>io.rest-assured</groupId>
        <artifactId>spring-mock-mvc</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- WireMock — simular serviços externos (para testar SSRF) -->
    <dependency>
        <groupId>org.wiremock</groupId>
        <artifactId>wiremock-standalone</artifactId>
        <version>3.5.4</version>
        <scope>test</scope>
    </dependency>

    <!-- Awaitility — testar código assíncrono (audit logs async) -->
    <dependency>
        <groupId>org.awaitility</groupId>
        <artifactId>awaitility</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- AssertJ — assertions fluentes -->
    <dependency>
        <groupId>org.assertj</groupId>
        <artifactId>assertj-core</artifactId>
        <scope>test</scope>
    </dependency>

    <!-- Faker — gerar dados realistas nos testes -->
    <dependency>
        <groupId>net.datafaker</groupId>
        <artifactId>datafaker</artifactId>
        <version>2.2.2</version>
        <scope>test</scope>
    </dependency>
</dependencies>

<build>
    <plugins>
        <!-- JaCoCo -->
        <plugin>
            <groupId>org.jacoco</groupId>
            <artifactId>jacoco-maven-plugin</artifactId>
            <version>0.8.12</version>
        </plugin>
    </plugins>
</build>
```

---

## Base de Teste — Classes Compartilhadas

Antes de gerar qualquer teste, criar a base compartilhada:

```java
// test/java/.../BaseSecurityIntegrationTest.java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@Transactional  // rollback automático após cada teste
public abstract class BaseSecurityIntegrationTest {

    @Container
    static MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
        .withDatabaseName("testdb")
        .withUsername("test")
        .withPassword("test")
        .withReuse(true); // reusar container entre suites — mais rápido

    @Autowired protected MockMvc mockMvc;
    @Autowired protected ObjectMapper objectMapper;
    @Autowired protected UserRepository userRepository;
    @Autowired protected SecurityAuditLogRepository auditLogRepository;
    @Autowired protected PasswordEncoder passwordEncoder;

    protected final DataFaker faker = new DataFaker();

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", mysql::getJdbcUrl);
        registry.add("spring.datasource.username", mysql::getUsername);
        registry.add("spring.datasource.password", mysql::getPassword);
        // Secrets de teste — nunca reais
        registry.add("jwt.secret", () -> Base64.getEncoder()
            .encodeToString(new byte[64])); // 512-bit dummy key
        registry.add("admin.route.secret", () -> "test-admin-secret-do-not-use-in-prod");
        registry.add("app.allowed-origins", () -> "http://localhost:3000");
    }

    // Helper: autenticar e obter token
    protected String authenticateAndGetToken(String email, String password) throws Exception {
        var result = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(
                    Map.of("email", email, "password", password,
                           "_hpf", "",      // honeypot vazio = humano
                           "_ts", 2500L,    // 2.5s = timing humano
                           "_mv", true))))
            .andExpect(status().isOk())
            .andReturn();

        return objectMapper.readTree(result.getResponse().getContentAsString())
            .get("accessToken").asText();
    }

    // Helper: criar usuário de teste
    protected User createTestUser(String email, String rawPassword) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
        user.setRole(Role.USER);
        user.setActive(true);
        return userRepository.save(user);
    }

    protected MockHttpServletRequestBuilder withAuth(
            MockHttpServletRequestBuilder builder, String token) {
        return builder.header("Authorization", "Bearer " + token)
                      .header("X-Requested-With", "XMLHttpRequest");
    }
}
```

---

## Padrões Obrigatórios em Todo Teste de Segurança

1. **Testar o caminho feliz E o ataque** — nunca só um dos dois
2. **Verificar o HTTP status code exato** — não apenas "2xx"
3. **Verificar o corpo da resposta** — garantir que não vaza informação
4. **Verificar efeitos colaterais** — audit log foi gravado? Redis foi incrementado?
5. **Nomear testes descritivamente** — `dado_tentativaDeLoginComSenhaErrada_entao_retornar401ComMensagemGenerica()`
6. **Usar `@Nested`** para agrupar cenários por feature
7. **Usar `@ParameterizedTest`** para múltiplos payloads de ataque

---

## Threshold de Cobertura JaCoCo (Mínimo Aceitável)

```
Pacote security.*          → 90% branch coverage
Pacote filter.*            → 85% branch coverage
Pacote service.auth.*      → 90% branch coverage
Overall instruction        → 80%
```

Leia `tests-jacoco-config.md` para a configuração completa.
