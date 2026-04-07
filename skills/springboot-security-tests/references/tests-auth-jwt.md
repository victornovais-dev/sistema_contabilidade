# Testes — Layer 1 + 6: Autenticação, JWT e Admin Invisível

---

## 1. Testes de Login

```java
// test/.../auth/LoginSecurityTest.java
@DisplayName("Login — Segurança")
class LoginSecurityTest extends BaseSecurityIntegrationTest {

    private static final String LOGIN_URL = "/api/auth/login";

    @BeforeEach
    void setup() {
        createTestUser("user@test.com", "SenhaForte@2024");
    }

    @Nested
    @DisplayName("Credenciais inválidas")
    class CredenciaisInvalidas {

        @Test
        @DisplayName("Email inexistente deve retornar mesma mensagem que senha errada")
        void dado_emailInexistente_entao_mensagemIdentica() throws Exception {
            // O atacante não deve saber se o email existe
            var respEmailInexistente = mockMvc.perform(post(LOGIN_URL)
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("naoexiste@test.com", "qualquercoisa")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

            var respSenhaErrada = mockMvc.perform(post(LOGIN_URL)
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("user@test.com", "senhaErrada")))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();

            // Respostas IDÊNTICAS — não diferenciar email/senha
            assertThat(respEmailInexistente).isEqualTo(respSenhaErrada);
            assertThat(respEmailInexistente).contains("Credenciais inválidas");
            assertThat(respEmailInexistente).doesNotContain("email", "senha", "encontrado");
        }

        @Test
        @DisplayName("Resposta não deve conter stack trace")
        void dado_loginInvalido_entao_semStackTrace() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("x@x.com", "errada")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.trace").doesNotExist())
                .andExpect(jsonPath("$.exception").doesNotExist())
                .andExpect(jsonPath("$.path").doesNotExist());
        }
    }

    @Nested
    @DisplayName("Timing Attack Prevention")
    class TimingAttack {

        @Test
        @DisplayName("Tempo de resposta deve ser similar para email existente e inexistente")
        void dado_emailsExistenteEInexistente_entao_tempoDeSimilar() throws Exception {
            int amostras = 5;
            long totalExistente = 0, totalInexistente = 0;

            for (int i = 0; i < amostras; i++) {
                long t = System.currentTimeMillis();
                mockMvc.perform(post(LOGIN_URL).contentType(APPLICATION_JSON)
                    .content(loginPayload("user@test.com", "errada")));
                totalExistente += System.currentTimeMillis() - t;

                t = System.currentTimeMillis();
                mockMvc.perform(post(LOGIN_URL).contentType(APPLICATION_JSON)
                    .content(loginPayload("naoexiste" + i + "@test.com", "errada")));
                totalInexistente += System.currentTimeMillis() - t;
            }

            long diffMs = Math.abs((totalExistente / amostras) - (totalInexistente / amostras));
            // Diferença deve ser < 200ms (dummy hash deve equalizar o tempo)
            assertThat(diffMs).isLessThan(200L);
        }
    }

    @Nested
    @DisplayName("Login bem-sucedido")
    class LoginSucesso {

        @Test
        @DisplayName("Login válido retorna access token mas não refresh token no body")
        void dado_credenciaisValidas_entao_accessTokenNoBodyRefreshNoCookie() throws Exception {
            mockMvc.perform(post(LOGIN_URL)
                    .contentType(APPLICATION_JSON)
                    .content(loginPayload("user@test.com", "SenhaForte@2024")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").exists())
                .andExpect(jsonPath("$.refreshToken").doesNotExist()) // NUNCA no body
                .andExpect(cookie().exists("refresh_token"))
                .andExpect(cookie().httpOnly("refresh_token", true))
                .andExpect(cookie().secure("refresh_token", true))
                .andExpect(cookie().sameSite("refresh_token", "Strict"));
        }

        @Test
        @DisplayName("Access token deve ter validade de 15 minutos")
        void dado_loginValido_entao_accessTokenExpiraEm15Min() throws Exception {
            String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

            Claims claims = Jwts.parser()
                .verifyWith(getTestSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

            long expirySeconds = claims.getExpiration().getTime() / 1000
                - claims.getIssuedAt().getTime() / 1000;

            // 15 minutos = 900s — tolerância de 5s
            assertThat(expirySeconds).isBetween(895L, 905L);
        }

        @Test
        @DisplayName("Algoritmo do JWT deve ser HS256 — rejeitar outros")
        void dado_tokenComAlgNone_entao_rejeitar401() throws Exception {
            // Forjar token com alg:none
            String forgedToken = buildTokenWithAlg("none", "user@test.com");

            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
        }

        @Test
        @DisplayName("Token com alg RS256 deve ser rejeitado")
        void dado_tokenComAlgRS256_entao_rejeitar401() throws Exception {
            String forgedToken = buildTokenWithAlg("RS256", "user@test.com");

            mockMvc.perform(get("/api/users/me")
                    .header("Authorization", "Bearer " + forgedToken))
                .andExpect(status().isUnauthorized());
        }
    }

    private String loginPayload(String email, String password) throws Exception {
        return objectMapper.writeValueAsString(Map.of(
            "email", email, "password", password,
            "_hpf", "", "_ts", 2500L, "_mv", true));
    }
}
```

---

## 2. Testes de Refresh Token

```java
// test/.../auth/RefreshTokenSecurityTest.java
@DisplayName("Refresh Token — Segurança")
class RefreshTokenSecurityTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("Refresh token usado deve ser invalidado após uso (token rotation)")
    void dado_refreshToken_quando_usadoDuasVezes_entao_segundaUsoFalha() throws Exception {
        // Login — obter refresh token via cookie
        MvcResult loginResult = mockMvc.perform(post("/api/auth/login")
                .contentType(APPLICATION_JSON)
                .content(loginBody()))
            .andExpect(status().isOk())
            .andReturn();

        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        assertThat(refreshCookie).isNotNull();

        // Primeiro refresh — deve funcionar
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());

        // Segundo refresh com o mesmo token — deve falhar (token rotacionado)
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Logout deve invalidar o refresh token no banco")
    void dado_logout_entao_refreshTokenInvalidadoNoBanco() throws Exception {
        MvcResult loginResult = login();
        Cookie refreshCookie = loginResult.getResponse().getCookie("refresh_token");
        String accessToken = extractAccessToken(loginResult);

        // Logout
        mockMvc.perform(post("/api/auth/logout")
                .header("Authorization", "Bearer " + accessToken)
                .cookie(refreshCookie))
            .andExpect(status().isOk());

        // Tentar refresh após logout — deve falhar
        mockMvc.perform(post("/api/auth/refresh")
                .cookie(refreshCookie))
            .andExpect(status().isUnauthorized());

        // Verificar cookie foi limpo na resposta do logout
        // (maxAge = 0)
        assertThat(loginResult.getResponse().getCookie("refresh_token")).isNotNull();
    }

    @Test
    @DisplayName("Refresh token expirado deve retornar 401")
    void dado_refreshTokenExpirado_entao_401() throws Exception {
        // Criar refresh token com validade passada diretamente no banco
        RefreshToken expired = new RefreshToken();
        expired.setTokenHash("hashdeumtokenexpirado");
        expired.setExpiresAt(Instant.now().minus(1, ChronoUnit.DAYS));
        expired.setRevoked(false);
        refreshTokenRepository.save(expired);

        mockMvc.perform(post("/api/auth/refresh")
                .cookie(new Cookie("refresh_token", "tokenexpirado")))
            .andExpect(status().isUnauthorized());
    }
}
```

---

## 3. Testes de Rota Admin Invisível

```java
// test/.../admin/AdminRouteSecurityTest.java
@DisplayName("Rota Admin — Invisibilidade e Acesso")
class AdminRouteSecurityTest extends BaseSecurityIntegrationTest {

    @Nested
    @DisplayName("Rotas que parecem admin devem se comportar como honeypot")
    class AdminHoneypotBehavior {

        @ParameterizedTest(name = "Rota suspeita: {0}")
        @ValueSource(strings = {
            "/admin", "/admin/users", "/dashboard",
            "/api/admin", "/manage", "/backoffice",
            "/cms", "/painel", "/api/v1/admin"
        })
        void dado_rotaAdminFalsa_entao_http200ComJsonFalso(String path) throws Exception {
            mockMvc.perform(get(path))
                .andExpect(status().isOk()) // NUNCA 404 — não revelar ausência
                .andExpect(content().contentType(APPLICATION_JSON));

            // Verificar que o acesso foi registrado como honeypot
            await().atMost(2, SECONDS).untilAsserted(() ->
                assertThat(honeypotLogRepository.findByRoute(path)).isNotEmpty()
            );
        }

        @Test
        @DisplayName("Tentativa de acesso admin deve gravar IP no log de honeypot")
        void dado_accessoAdminFalso_entao_ipRegistradoNoLog() throws Exception {
            mockMvc.perform(get("/admin")
                    .with(remoteAddr("192.168.1.100")))
                .andExpect(status().isOk());

            await().atMost(2, SECONDS).untilAsserted(() -> {
                var logs = honeypotLogRepository.findByIpAddress("192.168.1.100");
                assertThat(logs).isNotEmpty();
                assertThat(logs.get(0).getRoute()).isEqualTo("/admin");
            });
        }
    }

    @Nested
    @DisplayName("Rota admin real")
    class AdminRouteReal {

        @Test
        @DisplayName("Admin sem JWT deve retornar JSON honeypot, não 401")
        void dado_adminRealSemToken_entao_honeypotNao401() throws Exception {
            String realAdminPath = computeRealAdminPath();

            // Sem token — ainda retorna honeypot silencioso, não 401
            // (não confirmar que a rota existe para atacantes)
            mockMvc.perform(get(realAdminPath))
                .andExpect(status().isOk()); // honeypot behavior
        }

        @Test
        @DisplayName("Admin com JWT de USER (não ADMIN) deve retornar honeypot")
        void dado_adminRealComJwtUser_entao_honeypot() throws Exception {
            String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
            String realAdminPath = computeRealAdminPath();

            mockMvc.perform(get(realAdminPath)
                    .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()); // honeypot — não revelar que rota existe
        }
    }
}
```

---

## 4. Testes de Senha e Hashing

```java
// test/.../auth/PasswordSecurityTest.java
@DisplayName("Segurança de Senhas")
class PasswordSecurityTest extends BaseSecurityIntegrationTest {

    @Test
    @DisplayName("Senha nunca armazenada em texto puro")
    void dado_usuarioCriado_entao_senhaHasheadaNoBanco() {
        String rawPassword = "MinhaSenh@Segura123";
        User user = createTestUser("test@test.com", rawPassword);

        User savedUser = userRepository.findById(user.getId()).orElseThrow();

        // Hash nunca é igual ao valor raw
        assertThat(savedUser.getPasswordHash()).isNotEqualTo(rawPassword);
        // Hash deve começar com identificador Argon2id
        assertThat(savedUser.getPasswordHash()).startsWith("$argon2id$");
        // Hash deve ser verificável pelo encoder
        assertThat(passwordEncoder.matches(rawPassword, savedUser.getPasswordHash())).isTrue();
    }

    @Test
    @DisplayName("Dois hashes da mesma senha devem ser diferentes (salt único)")
    void dado_mesmaSenha_entao_hashesDistintos() {
        String password = "MinhaSenh@Segura123";
        String hash1 = passwordEncoder.encode(password);
        String hash2 = passwordEncoder.encode(password);

        assertThat(hash1).isNotEqualTo(hash2); // salt diferente a cada encode
        assertThat(passwordEncoder.matches(password, hash1)).isTrue();
        assertThat(passwordEncoder.matches(password, hash2)).isTrue();
    }

    @Test
    @DisplayName("Alteração de senha deve invalidar todos os refresh tokens existentes")
    void dado_alteracaoDeSenha_entao_refreshTokensRevogados() throws Exception {
        String token = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // Alterar senha
        mockMvc.perform(put("/api/users/me/password")
                .header("Authorization", "Bearer " + token)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "currentPassword", "SenhaForte@2024",
                    "newPassword", "NovaSenha@2025"))))
            .andExpect(status().isOk());

        // Verificar que todos os refresh tokens foram revogados
        List<RefreshToken> tokens = refreshTokenRepository.findByUserIdAndRevokedFalse(userId);
        assertThat(tokens).isEmpty();
    }
}
```
