# Testes — Layer 2 + 3 + 7: Rate Limiting, Honeypots e Detecção de Bot

---

## 1. Testes de Rate Limiting

```java
// test/.../ratelimit/RateLimitingSecurityTest.java
@DisplayName("Rate Limiting — Anti-Brute Force")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class RateLimitingSecurityTest extends BaseSecurityIntegrationTest {

    // IMPORTANTE: testes de rate limiting NÃO podem usar @Transactional
    // porque o Redis precisa persistir entre requests da mesma suite
    // Override da classe base:
    @Override
    @BeforeEach
    void setup() {
        redisTemplate.getConnectionFactory().getConnection().flushAll(); // limpar Redis
        createTestUser("victim@test.com", "SenhaForte@2024");
    }

    @Nested
    @DisplayName("Login — Lockout Progressivo")
    class LoginLockout {

        @Test
        @DisplayName("Após 5 tentativas falhas, deve retornar resposta genérica (lockout silencioso)")
        void dado_5TentativasFalhas_entao_lockoutSilencioso() throws Exception {
            String ip = "10.0.0.1";

            // 5 tentativas com senha errada
            for (int i = 0; i < 5; i++) {
                mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(APPLICATION_JSON)
                        .content(loginPayload("victim@test.com", "senhaErrada")))
                    .andExpect(status().isUnauthorized());
            }

            // 6ª tentativa — mesmo com senha correta, deve retornar lockout silencioso
            // HTTP 200 com mensagem genérica (não revelar que está bloqueado)
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(ip))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "SenhaForte@2024"))) // senha certa!
                .andExpect(status().isOk()) // 200, não 429 (não revelar lockout)
                .andExpect(jsonPath("$.error").value("Credenciais inválidas"));
        }

        @Test
        @DisplayName("Lockout não deve afetar IPs diferentes (não global)")
        void dado_lockoutEmUmIp_entao_outroIpFunciona() throws Exception {
            // IP 1 → lockout
            for (int i = 0; i < 6; i++) {
                mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr("10.0.0.1"))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "errada")));
            }

            // IP 2 → ainda funciona
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr("10.0.0.2"))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        }

        @Test
        @DisplayName("Login bem-sucedido reseta contador de tentativas")
        void dado_loginBemSucedido_entao_contadorResetado() throws Exception {
            String ip = "10.0.0.3";

            // 3 falhas
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(ip))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "errada")));
            }

            // Login correto — reseta contador
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(ip))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk());

            // Mais 4 falhas (até 4, não 5) — não deve estar bloqueado ainda
            for (int i = 0; i < 4; i++) {
                var result = mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(APPLICATION_JSON)
                        .content(loginPayload("victim@test.com", "errada")))
                    .andReturn();
                // Deve continuar respondendo normalmente (não lockout)
                assertThat(result.getResponse().getStatus()).isEqualTo(401);
            }
        }
    }

    @Nested
    @DisplayName("Rate Limit por Endpoint")
    class EndpointRateLimit {

        @ParameterizedTest(name = "Endpoint {0}: limite {1} requests por {2}")
        @CsvSource({
            "/api/auth/register, 3, 1h",
            "/api/auth/recover, 3, 1h",
            "/api/posts, 20, 1h"
        })
        void dado_limiteExcedido_entao_respostaGenerica(
                String endpoint, int limite, String janela) throws Exception {

            String ip = "10.0.0.99";

            // Atingir o limite
            for (int i = 0; i < limite; i++) {
                mockMvc.perform(post(endpoint)
                    .with(remoteAddr(ip))
                    .contentType(APPLICATION_JSON)
                    .content("{}"));
            }

            // Request além do limite
            var result = mockMvc.perform(post(endpoint)
                    .with(remoteAddr(ip))
                    .contentType(APPLICATION_JSON)
                    .content("{}"))
                .andReturn();

            // Soft block: 200 com mensagem, não 429 (não revelar rate limit)
            assertThat(result.getResponse().getStatus()).isEqualTo(200);
            String body = result.getResponse().getContentAsString();
            assertThat(body).doesNotContain("rate limit", "too many", "blocked");
        }

        @Test
        @DisplayName("Fingerprint multi-sinal — mesmo IP mas fingerprint diferente não bloqueia")
        void dado_mesmoIpFingerprintDiferente_entao_contadoresSeparados() throws Exception {
            // Simular dois clientes no mesmo IP mas fingerprints diferentes
            // (café, NAT corporativo)
            String ip = "10.0.0.50";

            // Cliente A — fingerprint com User-Agent Chrome
            for (int i = 0; i < 3; i++) {
                mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(ip))
                    .header("User-Agent", "Mozilla/5.0 Chrome/120")
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("x@x.com", "errada")));
            }

            // Cliente B — fingerprint com User-Agent Firefox — contador independente
            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr(ip))
                    .header("User-Agent", "Mozilla/5.0 Firefox/120")
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("victim@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        }
    }
}
```

---

## 2. Testes de Honeypots Backend

```java
// test/.../honeypot/HoneypotFilterTest.java
@DisplayName("Honeypots Backend")
class HoneypotFilterTest extends BaseSecurityIntegrationTest {

    @ParameterizedTest(name = "Rota honeypot: {0}")
    @ValueSource(strings = {
        "/wp-admin", "/wp-login.php", "/.env", "/.git",
        "/phpmyadmin", "/config.php", "/shell", "/eval",
        "/api/debug", "/swagger", "/actuator/env",
        "/api/v1/admin", "/install", "/backup"
    })
    @DisplayName("Toda rota honeypot deve retornar HTTP 200 com JSON falso")
    void dado_rotaHoneypot_entao_200ComJsonFalso(String path) throws Exception {
        mockMvc.perform(get(path)
                .with(remoteAddr("1.2.3.4"))
                .header("User-Agent", "sqlmap/1.7"))
            .andExpect(status().isOk())
            .andExpect(content().contentType(APPLICATION_JSON))
            // Verificar que é JSON válido mas vazio/falso
            .andExpect(jsonPath("$").isNotEmpty());
    }

    @Test
    @DisplayName("Acesso honeypot deve gravar log completo")
    void dado_acessoHoneypot_entao_logCompletoGravado() throws Exception {
        String testIp = "5.5.5.5";
        String testPath = "/wp-admin";
        String testAgent = "Nikto/2.1.6";

        mockMvc.perform(get(testPath)
                .with(remoteAddr(testIp))
                .header("User-Agent", testAgent)
                .header("X-Custom-Header", "test-value"))
            .andExpect(status().isOk());

        await().atMost(3, SECONDS).untilAsserted(() -> {
            List<HoneypotLog> logs = honeypotLogRepository.findByIpAddress(testIp);
            assertThat(logs).hasSize(1);

            HoneypotLog log = logs.get(0);
            assertThat(log.getRoute()).isEqualTo(testPath);
            assertThat(log.getUserAgent()).isEqualTo(testAgent);
            assertThat(log.getHeadersJson()).contains("X-Custom-Header");
            assertThat(log.getTimestamp()).isNotNull();
            assertThat(log.getIpAddress()).isEqualTo(testIp);
        });
    }

    @Test
    @DisplayName("Honeypot não deve vazar informação sobre a stack tecnológica real")
    void dado_rotaHoneypot_entao_semHeadersTecnologicos() throws Exception {
        mockMvc.perform(get("/wp-admin"))
            .andExpect(status().isOk())
            .andExpect(header().doesNotExist("X-Powered-By"))
            .andExpect(header().doesNotExist("Server"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"));
    }

    @Test
    @DisplayName("Rota real não deve ser interceptada pelo filtro honeypot")
    void dado_rotaReal_entao_naoInterceptadaPeloHoneypot() throws Exception {
        // A rota de login é real — não deve virar honeypot
        mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginPayload("naoexiste@test.com", "errada")))
            .andExpect(status().isUnauthorized()); // 401, não 200 honeypot
    }
}
```

---

## 3. Testes de Detecção de Bot (Layer 7)

```java
// test/.../bot/BotDetectionTest.java
@DisplayName("Detecção de Bot — Sinais do Frontend")
class BotDetectionTest extends BaseSecurityIntegrationTest {

    @Nested
    @DisplayName("Honeypot de campo de formulário")
    class FormHoneypot {

        @Test
        @DisplayName("Campo honeypot preenchido deve ser detectado como bot")
        void dado_honeypotPreenchido_entao_botDetectadoSemAlertar() throws Exception {
            var result = mockMvc.perform(post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@test.com",
                        "password", "SenhaForte@2024",
                        "_hpf", "preenchido_por_bot",  // ← honeypot preenchido
                        "_ts", 500L,
                        "_mv", false))))
                .andReturn();

            // Deve parecer resposta normal — não revelar detecção
            assertThat(result.getResponse().getStatus()).isIn(200, 401);

            // Mas event de bot deve ter sido publicado
            await().atMost(2, SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByEventType(BOT_DETECTED)).isNotEmpty()
            );
        }

        @Test
        @DisplayName("Formulário preenchido em < 800ms deve ser detectado como bot")
        void dado_formularioPreenchidoMuitoRapido_entao_botDetectado() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "email", "user@test.com",
                        "password", "SenhaForte@2024",
                        "_hpf", "",        // honeypot vazio (tenta burlar)
                        "_ts", 200L,       // 200ms — impossível para humano
                        "_mv", false))))
                .andReturn();

            await().atMost(2, SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByEventType(BOT_DETECTED)).isNotEmpty()
            );
        }

        @Test
        @DisplayName("Human com tempo normal e mouse movido deve passar")
        void dado_comportamentoHumano_entao_loginPermitido() throws Exception {
            createTestUser("human@test.com", "SenhaForte@2024");

            mockMvc.perform(post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "email", "human@test.com",
                        "password", "SenhaForte@2024",
                        "_hpf", "",       // vazio
                        "_ts", 4200L,     // 4.2s — humano normal
                        "_mv", true,      // mouse movimentou
                        "_kc", 25))))     // teclas pressionadas
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists());
        }
    }

    @Nested
    @DisplayName("Crawler trap")
    class CrawlerTrap {

        @Test
        @DisplayName("Acesso ao link invisível deve registrar crawler e retornar 200")
        void dado_accessoCrawlerLink_entao_registrarERetornar200() throws Exception {
            mockMvc.perform(get("/api/crawler-trap/secret-resource-7f3k2")
                    .with(remoteAddr("6.6.6.6")))
                .andExpect(status().isOk());

            await().atMost(2, SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByEventType(CRAWLER_DETECTED)
                    .stream().anyMatch(l -> l.getIpAddress().equals("6.6.6.6"))
                ).isTrue()
            );
        }
    }
}
```
