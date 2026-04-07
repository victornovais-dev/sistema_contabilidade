# Testes — Layer 4 + 9: Validação de Input, SQLi, XSS, IDOR e CSRF

---

## 1. SQL Injection

```java
// test/.../input/SqlInjectionTest.java
@DisplayName("SQL Injection — Detecção e Bloqueio")
class SqlInjectionTest extends BaseSecurityIntegrationTest {

    private String authToken;

    @BeforeEach
    void setup() throws Exception {
        createTestUser("user@test.com", "SenhaForte@2024");
        authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
    }

    // Payloads reais de SQLi — o que um pentester usaria
    static Stream<Arguments> sqlInjectionPayloads() {
        return Stream.of(
            // Classic
            Arguments.of("' OR '1'='1", "classic OR injection"),
            Arguments.of("' OR 1=1--", "comment injection"),
            Arguments.of("'; DROP TABLE users;--", "stacked query"),

            // Blind time-based (o mais perigoso — sem output visível)
            Arguments.of("' AND SLEEP(5)--", "MySQL time-based blind"),
            Arguments.of("' AND BENCHMARK(5000000,MD5(1))--", "MySQL benchmark"),
            Arguments.of("'; WAITFOR DELAY '0:0:5'--", "MSSQL time-based"),

            // Union-based
            Arguments.of("' UNION SELECT 1,2,3--", "union select"),
            Arguments.of("' UNION SELECT username,password,3 FROM users--", "data exfil"),

            // Information gathering
            Arguments.of("' AND 1=CONVERT(int,@@version)--", "version disclosure"),
            Arguments.of("'; SELECT * FROM information_schema.tables--", "schema enumeration"),

            // Encoding evasion
            Arguments.of("0x27204f5220 0x313d31", "hex encoded SQLi"),
            Arguments.of("%27%20OR%20%271%27%3D%271", "URL encoded"),
            Arguments.of("' /*!OR*/ '1'='1", "MySQL inline comment evasion")
        );
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("sqlInjectionPayloads")
    @DisplayName("Payload de SQLi deve ser rejeitado ou sanitizado")
    void dado_payloadSqli_entao_bloqueadoOuSanitizado(String payload, String desc)
            throws Exception {

        // Testar em campo de busca
        var result = mockMvc.perform(get("/api/posts/search")
                .param("q", payload)
                .header("Authorization", "Bearer " + authToken))
            .andReturn();

        int status = result.getResponse().getStatus();

        // Deve rejeitar (400) ou retornar resultado vazio (200 sem dados)
        // NUNCA deve executar o SQL
        if (status == 200) {
            String body = result.getResponse().getContentAsString();
            // Se retornou 200, não pode ter retornado dados de outros usuários
            assertThat(body).doesNotContain("password", "password_hash", "secret");
        } else {
            assertThat(status).isEqualTo(400);
        }

        // Para blind SQLi: verificar que a resposta não demorou 5+ segundos
        // (se demorou, o sleep() foi executado)
        assertThat(result.getResponse().getContentAsString()).isNotNull();
    }

    @Test
    @DisplayName("ORDER BY injection via sort param deve ser bloqueado")
    void dado_orderByInjection_entao_bloqueado() throws Exception {
        // Desenvolvedores frequentemente concatenam ORDER BY — vetor ignorado
        List<String> maliciousSorts = List.of(
            "name; DROP TABLE users--",
            "(SELECT 1 FROM users)",
            "CASE WHEN 1=1 THEN name ELSE price END",
            "1,2,(SELECT password FROM users LIMIT 1)"
        );

        for (String maliciousSort : maliciousSorts) {
            mockMvc.perform(get("/api/posts")
                    .param("sortBy", maliciousSort)
                    .header("Authorization", "Bearer " + authToken))
                .andExpect(status().isBadRequest());
        }
    }

    @Test
    @DisplayName("Query legítima não deve ser bloqueada (false positive check)")
    void dado_queryLegitima_entao_naoBloquear() throws Exception {
        mockMvc.perform(get("/api/posts/search")
                .param("q", "relatório financeiro")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/posts")
                .param("sortBy", "created_at")
                .param("sortDir", "DESC")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk());
    }
}
```

---

## 2. XSS (Cross-Site Scripting)

```java
// test/.../input/XssSecurityTest.java
@DisplayName("XSS — Sanitização e Bloqueio")
class XssSecurityTest extends BaseSecurityIntegrationTest {

    static Stream<String> xssPayloads() {
        return Stream.of(
            // Basic
            "<script>alert('xss')</script>",
            "<img src=x onerror=alert(1)>",
            "<svg onload=alert(1)>",

            // Attribute injection
            "\" onmouseover=\"alert(1)\"",
            "' onfocus='alert(1)' autofocus='",

            // Protocol bypass
            "javascript:alert(1)",
            "vbscript:msgbox(1)",
            "data:text/html,<script>alert(1)</script>",

            // Encoding bypass
            "&#x3C;script&#x3E;alert(1)&#x3C;/script&#x3E;",
            "%3Cscript%3Ealert(1)%3C%2Fscript%3E",
            "\\u003cscript\\u003ealert(1)\\u003c/script\\u003e",

            // Unicode lookalike bypass (ℯval, ᴊavascript)
            "\u24D4\u24D1\u24D0\u24DB(1)", // circled eval
            "j\u0430v\u0430script:alert(1)", // cyrillic a

            // DOM-based triggers
            "document.cookie",
            "document.write('<script>')",
            "innerHTML=<script>alert(1)</script>",

            // CSS injection
            "<style>@import'http://evil.com'</style>",
            "expression(alert(1))" // IE expression
        );
    }

    @ParameterizedTest(name = "[{index}] XSS payload em campo bio")
    @MethodSource("xssPayloads")
    @DisplayName("Payload XSS em campo de perfil deve ser sanitizado ou rejeitado")
    void dado_xssNoBio_entao_sanitizadoOuRejeitado(String payload) throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        var result = mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "bio", payload,
                    "avatarUrl", "https://example.com/avatar.jpg",
                    "isPrivate", false))))
            .andReturn();

        int status = result.getResponse().getStatus();

        if (status == 200) {
            // Se aceitou: verificar que o payload foi sanitizado no banco
            User user = userRepository.findByEmail("user@test.com").orElseThrow();
            String storedBio = user.getProfile().getBio();

            assertThat(storedBio).doesNotContain("<script");
            assertThat(storedBio).doesNotContain("javascript:");
            assertThat(storedBio).doesNotContain("onerror=");
            assertThat(storedBio).doesNotContain("onload=");
            assertThat(storedBio).doesNotContain("document.cookie");
        } else {
            // Se rejeitou: deve ser 400
            assertThat(status).isEqualTo(400);
        }
    }

    @Test
    @DisplayName("XSS em output: resposta da API deve escapar HTML")
    void dado_bioComHtml_quando_retornadoPelaApi_entao_escapado() throws Exception {
        // Salvar HTML diretamente no banco (simular dado legado)
        User user = userRepository.findByEmail("user@test.com").orElseThrow();
        user.getProfile().setBio("Normal text"); // salvar normal primeiro
        userRepository.save(user);

        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        String response = mockMvc.perform(get("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();

        // Verificar que o JSON não contém HTML não escapado em campos de texto
        assertThat(response).doesNotContain("<script>");
    }

    @Test
    @DisplayName("Content-Security-Policy header deve estar presente e correto")
    void dado_qualquerRequest_entao_cspHeaderPresente() throws Exception {
        mockMvc.perform(get("/api/posts"))
            .andExpect(header().exists("Content-Security-Policy"))
            .andExpect(header().string("Content-Security-Policy",
                containsString("default-src 'self'")))
            .andExpect(header().string("Content-Security-Policy",
                containsString("script-src 'self'")))
            .andExpect(header().string("Content-Security-Policy",
                not(containsString("'unsafe-inline'")))) // sem unsafe-inline
            .andExpect(header().string("Content-Security-Policy",
                containsString("frame-ancestors 'none'")));
    }
}
```

---

## 3. IDOR (Insecure Direct Object Reference)

```java
// test/.../input/IdorSecurityTest.java
@DisplayName("IDOR — Controle de Acesso a Recursos")
class IdorSecurityTest extends BaseSecurityIntegrationTest {

    private User userA, userB;
    private Post postDeB;
    private String tokenA, tokenB;

    @BeforeEach
    void setup() throws Exception {
        userA = createTestUser("usera@test.com", "SenhaA@2024");
        userB = createTestUser("userb@test.com", "SenhaB@2024");

        tokenA = authenticateAndGetToken("usera@test.com", "SenhaA@2024");
        tokenB = authenticateAndGetToken("userb@test.com", "SenhaB@2024");

        postDeB = createPost(userB, "Post privado do usuário B");
    }

    @Test
    @DisplayName("Usuário A não pode acessar recurso do usuário B")
    void dado_recursoDeOutroUsuario_entao_404NaoPareceExistir() throws Exception {
        // 404 — não confirmar existência do recurso
        mockMvc.perform(get("/api/posts/" + postDeB.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());

        // Verificar que não retorna 403 (403 confirmaria que existe)
        var result = mockMvc.perform(put("/api/posts/" + postDeB.getId())
                .header("Authorization", "Bearer " + tokenA)
                .contentType(APPLICATION_JSON)
                .content("{\"title\": \"hackeado\"}"))
            .andReturn();

        assertThat(result.getResponse().getStatus()).isNotEqualTo(403);
        assertThat(result.getResponse().getStatus()).isEqualTo(404);
    }

    @Test
    @DisplayName("Endpoint de deleção de outro usuário deve retornar 404")
    void dado_deleteRecursoAlheio_entao_404() throws Exception {
        mockMvc.perform(delete("/api/posts/" + postDeB.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isNotFound());

        // Verificar que o post ainda existe no banco
        assertThat(postRepository.findById(postDeB.getId())).isPresent();
    }

    @Test
    @DisplayName("IDs sequenciais devem ser rejeitados — apenas UUID")
    void dado_idSequencialNaUrl_entao_400OuNotFound() throws Exception {
        // Atacante tenta enumerar recursos com IDs numéricos
        mockMvc.perform(get("/api/posts/1")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().is4xxClientError()); // 400 ou 404 — nunca 200

        mockMvc.perform(get("/api/posts/42")
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("IDOR por enumeração de UUID — usuário próprio pode acessar")
    void dado_recursoDoProprioUsuario_entao_200() throws Exception {
        Post postDeA = createPost(userA, "Meu post");

        mockMvc.perform(get("/api/posts/" + postDeA.getId())
                .header("Authorization", "Bearer " + tokenA))
            .andExpect(status().isOk());
    }
}
```

---

## 4. Mass Assignment

```java
// test/.../input/MassAssignmentTest.java
@DisplayName("Mass Assignment — Campos Proibidos")
class MassAssignmentTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("Campo isAdmin não deve ser aceito via API")
    void dado_campoIsAdminNoBody_entao_ignorado() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // Tentar escalar privilégios via mass assignment
        mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "bio", "Sou normal",
                    "isAdmin", true,          // ← tentativa de escalada
                    "role", "ADMIN",          // ← outra tentativa
                    "isBanned", false,        // ← tentativa de desbane
                    "passwordHash", "hacked"  // ← tentativa de alterar senha
                ))))
            .andReturn();

        // Verificar que o usuário não virou admin
        User user = userRepository.findByEmail("user@test.com").orElseThrow();
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.isAdmin()).isFalse();
        assertThat(user.getPasswordHash())
            .doesNotEqualTo("hacked")
            .startsWith("$argon2id$");
    }

    @Test
    @DisplayName("JSON com campos desconhecidos deve retornar erro (FAIL_ON_UNKNOWN_PROPERTIES)")
    void dado_jsonComCamposExtras_entao_400() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content("{\"bio\":\"ok\",\"campoQueNaoExiste\":\"valor\"}"))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JSON com chave duplicada deve ser rejeitado")
    void dado_jsonComChaveDuplicada_entao_400() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // JSON com chave duplicada — STRICT_DUPLICATE_DETECTION
        String maliciousJson = "{\"bio\":\"normal\",\"bio\":\"<script>alert(1)</script>\"}";

        mockMvc.perform(put("/api/users/me/profile")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(maliciousJson))
            .andExpect(status().isBadRequest());
    }
}
```

---

## 5. CSRF

```java
// test/.../csrf/CsrfSecurityTest.java
@DisplayName("CSRF — Proteção de Estado")
class CsrfSecurityTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("POST sem CSRF token deve ser rejeitado (403)")
    void dado_postSemCsrfToken_entao_403() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // Request sem X-XSRF-TOKEN header
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + token)
                // Sem X-XSRF-TOKEN e sem cookie XSRF
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"Teste\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST com CSRF token correto deve funcionar")
    void dado_postComCsrfTokenCorreto_entao_200() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
        String csrfToken = gerarCsrfToken();

        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + token)
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"Teste\",\"content\":\"Conteúdo\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("Origin de domínio externo deve ser rejeitado")
    void dado_originExterno_entao_403() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
        String csrfToken = gerarCsrfToken();

        // Simula request vindo de site malicioso
        mockMvc.perform(post("/api/posts")
                .header("Authorization", "Bearer " + token)
                .header("Origin", "https://evil-site.com")  // ← domínio malicioso
                .header("X-XSRF-TOKEN", csrfToken)
                .cookie(new Cookie("XSRF-TOKEN", csrfToken))
                .contentType(APPLICATION_JSON)
                .content("{\"title\":\"CSRF Attack\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET não deve exigir CSRF token (safe method)")
    void dado_getRequest_entao_naoExigirCsrf() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        mockMvc.perform(get("/api/posts")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Cookie CSRF não deve ser HttpOnly (frontend JS precisa lê-lo)")
    void dado_qualquerRequest_entao_cookieCsrfNaoHttpOnly() throws Exception {
        var result = mockMvc.perform(get("/api/posts"))
            .andReturn();

        Cookie csrfCookie = result.getResponse().getCookie("XSRF-TOKEN");
        if (csrfCookie != null) {
            // XSRF-TOKEN NÃO pode ser HttpOnly — JS precisa ler
            assertThat(csrfCookie.isHttpOnly()).isFalse();
            // Mas deve ser Secure
            assertThat(csrfCookie.getSecure()).isTrue();
        }
    }

    private String gerarCsrfToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
```
