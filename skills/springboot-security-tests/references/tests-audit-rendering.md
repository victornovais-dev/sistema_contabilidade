# Testes — Layer 10 + 11 + 12: Auditoria, Renderização e Banco de Dados

---

## 1. Auditoria de Segurança (Layer 10)

```java
// test/.../audit/SecurityAuditTest.java
@DisplayName("Auditoria — Logs de Eventos de Segurança")
class SecurityAuditTest extends BaseSecurityIntegrationTest {

    @Nested
    @DisplayName("Eventos de login")
    class LoginAuditEvents {

        @Test
        @DisplayName("Login bem-sucedido deve gerar audit log com dados corretos")
        void dado_loginSucesso_entao_auditLogGerado() throws Exception {
            createTestUser("audit@test.com", "SenhaForte@2024");

            mockMvc.perform(post("/api/auth/login")
                    .with(remoteAddr("11.22.33.44"))
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("audit@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk());

            await().atMost(3, SECONDS).untilAsserted(() -> {
                List<SecurityAuditLog> logs = auditLogRepository
                    .findByEventType(LOGIN_SUCCESS);
                assertThat(logs).isNotEmpty();

                SecurityAuditLog log = logs.get(0);
                assertThat(log.getIpAddress()).isEqualTo("11.22.33.44");
                assertThat(log.getResult()).isEqualTo(AuditResult.SUCCESS);
                assertThat(log.getUserId()).isNotNull();
                assertThat(log.getOccurredAt()).isNotNull();
                assertThat(log.getDeviceFingerprint()).isNotNull();

                // Log NÃO deve conter senha ou token
                assertThat(log.getMetadata()).doesNotContain("SenhaForte@2024");
                assertThat(log.getMetadata()).doesNotContain("Bearer");
            });
        }

        @Test
        @DisplayName("Login falho deve gerar audit log sem expor email completo")
        void dado_loginFalho_entao_auditLogComEmailMascarado() throws Exception {
            mockMvc.perform(post("/api/auth/login")
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("usuario@empresa.com.br", "errada")))
                .andExpect(status().isUnauthorized());

            await().atMost(3, SECONDS).untilAsserted(() -> {
                List<SecurityAuditLog> logs = auditLogRepository
                    .findByEventType(LOGIN_FAILURE);
                assertThat(logs).isNotEmpty();

                SecurityAuditLog log = logs.get(0);
                // Email deve estar mascarado (apenas primeiros 3 chars)
                String meta = log.getMetadata();
                assertThat(meta).contains("usu***"); // ou similar mascaramento
                assertThat(meta).doesNotContain("usuario@empresa.com.br"); // nunca completo
            });
        }

        @Test
        @DisplayName("Múltiplos logins de IPs diferentes devem gerar logs separados")
        void dado_loginsDeIpsDiferentes_entao_logsIndividuais() throws Exception {
            createTestUser("multi@test.com", "SenhaForte@2024");

            String[] ips = {"1.1.1.1", "2.2.2.2", "3.3.3.3"};
            for (String ip : ips) {
                mockMvc.perform(post("/api/auth/login")
                        .with(remoteAddr(ip))
                        .contentType(APPLICATION_JSON)
                        .content(loginPayload("multi@test.com", "SenhaForte@2024")))
                    .andExpect(status().isOk());
            }

            await().atMost(3, SECONDS).untilAsserted(() -> {
                List<SecurityAuditLog> logs = auditLogRepository
                    .findByEventType(LOGIN_SUCCESS);
                Set<String> loggedIps = logs.stream()
                    .map(SecurityAuditLog::getIpAddress)
                    .collect(Collectors.toSet());
                assertThat(loggedIps).containsAll(Arrays.asList(ips));
            });
        }
    }

    @Nested
    @DisplayName("Eventos de ataque")
    class AttackAuditEvents {

        @Test
        @DisplayName("Tentativa de SQLi deve gerar audit log do tipo SQL_INJECTION_ATTEMPT")
        void dado_tentativaSqli_entao_auditLogCorreto() throws Exception {
            String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

            mockMvc.perform(get("/api/posts/search")
                    .param("q", "' OR 1=1--")
                    .header("Authorization", "Bearer " + token));

            await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByEventType(SQL_INJECTION_ATTEMPT))
                    .isNotEmpty()
            );
        }

        @Test
        @DisplayName("Audit logs não devem poder ser deletados via API")
        void dado_tentativaDeletarLogs_entao_endpointNaoExiste() throws Exception {
            String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

            // Endpoint de deleção de audit log não deve existir
            mockMvc.perform(delete("/api/admin/audit-logs")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());

            mockMvc.perform(delete("/api/security/logs")
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().is4xxClientError());
        }
    }

    @Nested
    @DisplayName("Detecção de device novo")
    class NewDeviceDetection {

        @Test
        @DisplayName("Login de device novo deve gerar evento UNUSUAL_LOCATION")
        void dado_loginDeDeviceNovo_entao_eventoIncomum() throws Exception {
            createTestUser("device@test.com", "SenhaForte@2024");

            // Login do device A
            mockMvc.perform(post("/api/auth/login")
                    .header("User-Agent", "Mozilla/5.0 Chrome/120")
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("device@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk());

            await().pollDelay(500, MILLISECONDS).atMost(3, SECONDS).until(() ->
                !auditLogRepository.findByEventType(LOGIN_SUCCESS).isEmpty());

            // Login do device B (fingerprint diferente)
            mockMvc.perform(post("/api/auth/login")
                    .header("User-Agent", "curl/7.88.1") // user agent totalmente diferente
                    .header("Accept-Language", "zh-CN")   // idioma diferente
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("device@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk());

            await().atMost(3, SECONDS).untilAsserted(() ->
                assertThat(auditLogRepository.findByEventType(UNUSUAL_LOCATION))
                    .isNotEmpty()
            );
        }
    }
}
```

---

## 2. Segurança de Renderização (Layer 11)

```java
// test/.../rendering/RenderingSecurityTest.java
@DisplayName("Segurança de Renderização — PDF, Markdown, Imagens")
class RenderingSecurityTest extends BaseSecurityIntegrationTest {

    private String authToken;

    @BeforeEach
    void setup() throws Exception {
        createTestUser("user@test.com", "SenhaForte@2024");
        authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
    }

    @Nested
    @DisplayName("Geração de PDF")
    class PdfGeneration {

        @Test
        @DisplayName("PDF com SSRF via tag img deve ter request bloqueado")
        void dado_htmlComImgSsrf_entao_pdfGeradoSemFazerRequest() throws Exception {
            // Este teste verifica que ao gerar PDF, o engine não faz requests externos
            // Configurar WireMock para detectar se algum request é feito
            WireMock.configureFor("localhost", 9998);
            stubFor(get(anyUrl()).willReturn(aResponse().withStatus(200)));

            String templateComSsrf = "<img src=\"http://localhost:9998/ssrf-attempt\">";

            mockMvc.perform(post("/api/reports/generate")
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "type", "custom",
                        "content", templateComSsrf))))
                .andReturn();

            // WireMock NÃO deve ter recebido nenhum request (BlockingUserAgent funcionou)
            verify(0, getRequestedFor(anyUrl()));
        }

        @Test
        @DisplayName("Dados do usuário em PDF devem ser sanitizados")
        void dado_dadosXssNoPdf_entao_sanitizadosAntesDaRenderizacao() throws Exception {
            // Criar relatório com dados maliciosos no nome do usuário
            var result = mockMvc.perform(post("/api/reports/invoice")
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "clientName", "<script>alert('XSS')</script>",
                        "amount", 100.00,
                        "description", "javascript:evil()"))))
                .andReturn();

            if (result.getResponse().getStatus() == 200) {
                byte[] pdfBytes = result.getResponse().getContentAsByteArray();
                String pdfContent = new String(pdfBytes, StandardCharsets.ISO_8859_1);

                // XSS não deve aparecer no PDF
                assertThat(pdfContent).doesNotContain("<script>");
                assertThat(pdfContent).doesNotContain("javascript:");
            }
        }
    }

    @Nested
    @DisplayName("Renderização de Markdown")
    class MarkdownRendering {

        @ParameterizedTest(name = "XSS em Markdown: {0}")
        @ValueSource(strings = {
            "[xss](javascript:alert(1))",           // link JS
            "![xss](javascript:alert(1))",          // image JS
            "<script>alert('markdown xss')</script>", // raw HTML
            "[click](data:text/html,<script>alert(1)</script>)", // data URI
            "**<img src=x onerror=alert(1)>**"      // HTML em bold
        })
        @DisplayName("XSS em input Markdown deve ser sanitizado no output HTML")
        void dado_xssNoMarkdown_entao_sanitizadoNoHtml(String maliciousMarkdown) throws Exception {
            var result = mockMvc.perform(post("/api/posts")
                    .header("Authorization", "Bearer " + authToken)
                    .header("X-XSRF-TOKEN", getCsrfToken())
                    .cookie(new Cookie("XSRF-TOKEN", getCsrfToken()))
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "title", "Post de teste",
                        "content", maliciousMarkdown))))
                .andReturn();

            if (result.getResponse().getStatus() == 201) {
                String postId = extractId(result);

                // Buscar o post e verificar o HTML renderizado
                String postResponse = mockMvc.perform(get("/api/posts/" + postId)
                        .header("Authorization", "Bearer " + authToken))
                    .andReturn().getResponse().getContentAsString();

                String renderedHtml = objectMapper.readTree(postResponse)
                    .get("renderedContent").asText();

                assertThat(renderedHtml).doesNotContain("<script");
                assertThat(renderedHtml).doesNotContain("javascript:");
                assertThat(renderedHtml).doesNotContain("onerror=");
                assertThat(renderedHtml).doesNotContain("data:text/html");
            }
        }

        @Test
        @DisplayName("Links em Markdown devem ter rel=noopener e target=_blank")
        void dado_linkNoMarkdown_entao_noopenerNoreferrer() throws Exception {
            String markdown = "[Google](https://google.com)";
            String rendered = markdownRenderer.render(markdown);

            assertThat(rendered).contains("rel=\"noopener noreferrer nofollow\"");
            assertThat(rendered).contains("target=\"_blank\"");
        }
    }

    @Nested
    @DisplayName("Template Injection (SSTI)")
    class TemplateInjection {

        @Test
        @DisplayName("Payload SSTI Thymeleaf deve ser rejeitado ou escapado")
        void dado_payloadSsti_entao_naoExecutado() throws Exception {
            // Payload clássico de SSTI em Spring/Thymeleaf
            String sstiPayload = "${T(java.lang.Runtime).getRuntime().exec('id')}";

            var result = mockMvc.perform(put("/api/users/me/profile")
                    .header("Authorization", "Bearer " + authToken)
                    .contentType(APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "bio", sstiPayload))))
                .andReturn();

            if (result.getResponse().getStatus() == 200) {
                // Se aceitou: verificar que o payload foi salvo como texto literal
                User user = userRepository.findByEmail("user@test.com").orElseThrow();
                // O bio deve ser o texto literal ou sanitizado — nunca o resultado de exec()
                String bio = user.getProfile().getBio();
                assertThat(bio).doesNotContain("uid="); // saída de exec('id') em Linux
                assertThat(bio).doesNotContain("root"); // não pode ter executado
            }
        }
    }
}
```

---

## 3. Banco de Dados — Hardening (Layer 12)

```java
// test/.../database/DatabaseSecurityTest.java
@DisplayName("Banco de Dados — Hardening e Privilégios")
@Testcontainers
class DatabaseSecurityTest extends BaseSecurityIntegrationTest {

    @Autowired
    private DataSource dataSource;

    @Test
    @DisplayName("Usuário da aplicação não deve ter privilégio DDL (DROP, CREATE, ALTER)")
    void dado_usuarioApp_entao_semPrivilegioDdl() throws Exception {
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {

            // Tentar DROP TABLE — deve lançar exceção de permissão
            assertThatThrownBy(() ->
                stmt.execute("DROP TABLE IF EXISTS users_temp"))
                .isInstanceOf(SQLException.class)
                .hasMessageContaining("command denied"); // MySQL: access denied for DROP
        }
    }

    @Test
    @DisplayName("Soft delete — DELETE real não deve remover registro do banco")
    void dado_deletarRecurso_entao_softDeleteNaoFisico() throws Exception {
        createTestUser("softdelete@test.com", "SenhaForte@2024");
        User user = userRepository.findByEmail("softdelete@test.com").orElseThrow();
        UUID userId = user.getId();

        // Deletar via API
        String token = authenticateAndGetToken("softdelete@test.com", "SenhaForte@2024");
        mockMvc.perform(delete("/api/users/me")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk());

        // Verificar soft delete: registro ainda existe com is_deleted=true
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT is_deleted, deleted_at FROM users WHERE id = ?")) {
            ps.setString(1, userId.toString());
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next()).isTrue();
            assertThat(rs.getBoolean("is_deleted")).isTrue();
            assertThat(rs.getTimestamp("deleted_at")).isNotNull();
        }
    }

    @Test
    @DisplayName("Queries nunca devem usar concatenação de string — verificar via explain")
    void dado_queryComParametro_entao_preparedStatement() throws Exception {
        // Este teste verifica que o ORM usa prepared statements
        // Habilitar log de queries do Hibernate para verificar
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // Busca com input que quebraria uma query concatenada
        mockMvc.perform(get("/api/posts/search")
                .param("q", "test'; DROP TABLE posts;--")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()); // deve funcionar, não quebrar

        // Verificar que a tabela posts ainda existe
        try (Connection conn = dataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM posts");
            assertThat(rs.next()).isTrue();
            // Se a tabela foi dropada, a query acima lançaria SQLException
        }
    }

    @Test
    @DisplayName("Listagem sempre deve ter paginação — nunca retornar tudo")
    void dado_listagemSemPaginacao_entao_defaultPaginacaoAplicada() throws Exception {
        // Criar 100 posts
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
        // (criar posts via repositório diretamente para velocidade)
        criarMuitosPosts(100);

        var result = mockMvc.perform(get("/api/posts")
                .header("Authorization", "Bearer " + token))
            .andExpect(status().isOk())
            .andReturn();

        var json = objectMapper.readTree(result.getResponse().getContentAsString());

        // Response deve ter paginação
        assertThat(json.has("content")).isTrue();
        assertThat(json.has("totalPages")).isTrue();
        assertThat(json.has("size")).isTrue();

        // Default page size não deve ser muito grande (proteção bulk extraction)
        int size = json.get("size").asInt();
        assertThat(size).isLessThanOrEqualTo(50); // máximo razoável

        // Número de itens retornados deve respeitar o page size
        int returnedItems = json.get("content").size();
        assertThat(returnedItems).isLessThanOrEqualTo(size);
    }

    @Test
    @DisplayName("Colunas sensíveis devem estar criptografadas no banco")
    void dado_dadoSensivel_entao_criptografadoNoBanco() throws Exception {
        // Criar usuário com CPF
        createTestUserWithCpf("cpf@test.com", "SenhaForte@2024", "123.456.789-00");

        // Verificar diretamente no banco — o CPF não deve estar em texto puro
        try (Connection conn = dataSource.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                "SELECT tax_id FROM user_profiles WHERE user_id = ?")) {
            ps.setString(1, getUserId("cpf@test.com").toString());
            ResultSet rs = ps.executeQuery();

            assertThat(rs.next()).isTrue();
            String storedValue = rs.getString("tax_id");

            // Deve estar criptografado (não é o CPF original)
            assertThat(storedValue).isNotEqualTo("123.456.789-00");
            assertThat(storedValue).isNotEqualTo("12345678900");
            // Deve ser um valor Base64 (ciphertext AES-GCM)
            assertThat(storedValue).matches("[A-Za-z0-9+/=]{30,}");
        }
    }

    @Test
    @DisplayName("Bulk data access deve gerar evento de segurança")
    void dado_queryRetornandoMuitosRegistros_entao_eventoGerado() throws Exception {
        criarMuitosPosts(1100); // acima do threshold de 1000
        String token = authenticateAndGetToken("admin@test.com", "AdminPass@2024");

        // Admin fazendo export de dados
        mockMvc.perform(get("/api/admin/export/posts")
                .header("Authorization", "Bearer " + token))
            .andReturn();

        await().atMost(3, SECONDS).untilAsserted(() ->
            assertThat(auditLogRepository.findByEventType(BULK_DATA_ACCESS))
                .isNotEmpty()
        );
    }
}
```
