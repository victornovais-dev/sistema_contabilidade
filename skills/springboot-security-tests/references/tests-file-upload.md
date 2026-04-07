# Testes — Layer 5 + 8: Upload de Arquivos, SSRF e Path Traversal

---

## 1. Upload de Arquivos — Magic Bytes e MIME

```java
// test/.../upload/FileUploadSecurityTest.java
@DisplayName("Upload de Arquivos — Segurança")
class FileUploadSecurityTest extends BaseSecurityIntegrationTest {

    private String authToken;

    @BeforeEach
    void setup() throws Exception {
        createTestUser("user@test.com", "SenhaForte@2024");
        authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
    }

    // ─── Helper: criar bytes de arquivo com magic bytes específicos ───

    private byte[] jpegBytes() {
        // Magic bytes JPEG válidos + conteúdo mínimo
        return new byte[]{(byte)0xFF, (byte)0xD8, (byte)0xFF, (byte)0xE0,
                          0x00, 0x10, 0x4A, 0x46, 0x49, 0x46, 0x00, 0x01};
    }

    private byte[] pngBytes() {
        return new byte[]{(byte)0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A};
    }

    private byte[] phpShellBytes() {
        return "<?php system($_GET['cmd']); ?>".getBytes(StandardCharsets.UTF_8);
    }

    private byte[] gifWithXss() {
        // GIFAR: magic bytes GIF + XSS embutido
        String content = "GIF89a" + "<script>alert('GIFAR')</script>";
        return content.getBytes(StandardCharsets.ISO_8859_1);
    }

    private byte[] zipBomb() throws IOException {
        // ZIP com ratio de compressão extremo (simulado pequeno para testes)
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            ZipEntry entry = new ZipEntry("bomb.txt");
            zos.putNextEntry(entry);
            // 10MB de zeros (comprime para ~10KB)
            byte[] zeros = new byte[10 * 1024 * 1024];
            zos.write(zeros);
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // ─── Testes de extensões perigosas ───

    @ParameterizedTest(name = "Extensão perigosa: .{0}")
    @ValueSource(strings = {"php", "php3", "php4", "phtml", "phar",
                            "jsp", "jspx", "asp", "aspx",
                            "exe", "bat", "sh", "py", "rb"})
    @DisplayName("Upload com extensão executável deve ser rejeitado")
    void dado_extensaoExecutavel_entao_rejeitado(String ext) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "malware." + ext,
            "image/jpeg",      // MIME spoofing: extensão .php mas diz image/jpeg
            jpegBytes()        // até com magic bytes válidos
        );

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value(containsString("não permitido")));
    }

    // ─── Testes de MIME spoofing ───

    @Test
    @DisplayName("PHP com MIME image/jpeg deve ser rejeitado (magic bytes mismatch)")
    void dado_phpDisfarçadoDeJpeg_entao_rejeitado() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file",
            "avatar.jpg",   // extensão parece imagem
            "image/jpeg",   // MIME parece imagem
            phpShellBytes() // mas conteúdo é PHP!
        );

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("JPEG legítimo deve ser aceito")
    void dado_jpegValido_entao_aceito() throws Exception {
        // Usar imagem JPEG real mínima válida
        byte[] validJpeg = createMinimalValidJpeg();

        MockMultipartFile file = new MockMultipartFile(
            "file", "avatar.jpg", "image/jpeg", validJpeg);

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.fileId").exists());
    }

    // ─── Polyglot files ───

    @Test
    @DisplayName("GIFAR (GIF com JS embutido) deve ser detectado e rejeitado")
    void dado_gifComXss_entao_rejeitado() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", "image.gif", "image/gif", gifWithXss());

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("PDF com JavaScript deve ser rejeitado")
    void dado_pdfComJavascript_entao_rejeitado() throws Exception {
        // PDF com /JS action
        String pdfWithJs = "%PDF-1.4\n1 0 obj\n<</Type /Catalog /OpenAction 2 0 R>>\n"
            + "endobj\n2 0 obj\n<</Type /Action /S /JavaScript /JS (app.alert('XSS'))>>\nendobj";

        MockMultipartFile file = new MockMultipartFile(
            "file", "document.pdf", "application/pdf",
            pdfWithJs.getBytes(StandardCharsets.ISO_8859_1));

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isBadRequest());
    }

    // ─── ZIP Bomb / Decompression Bomb ───

    @Test
    @DisplayName("Arquivo maior que o limite máximo deve ser rejeitado")
    void dado_arquivoMaiorQueLimite_entao_rejeitado() throws Exception {
        // 11MB — acima do limite de 10MB
        byte[] bigFile = new byte[11 * 1024 * 1024];
        Arrays.fill(bigFile, (byte) 0xFF);
        bigFile[0] = (byte) 0xFF; bigFile[1] = (byte) 0xD8; bigFile[2] = (byte) 0xFF; // JPEG header

        MockMultipartFile file = new MockMultipartFile(
            "file", "big.jpg", "image/jpeg", bigFile);

        mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isPayloadTooLarge());
    }

    // ─── Path Traversal via filename ───

    @ParameterizedTest(name = "Filename malicioso: {0}")
    @ValueSource(strings = {
        "../../../etc/passwd",
        "..%2F..%2F..%2Fetc%2Fpasswd",
        "....//....//....//etc/passwd",
        "file.jpg\u0000.php",    // null byte injection
        "CON.jpg",               // Windows reserved
        "NUL.jpg",
        "..\\..\\windows\\system32\\cmd.exe"
    })
    @DisplayName("Nome de arquivo malicioso deve ser sanitizado")
    void dado_filenameComPathTraversal_entao_sanitizado(String maliciousFilename) throws Exception {
        MockMultipartFile file = new MockMultipartFile(
            "file", maliciousFilename, "image/jpeg", jpegBytes());

        var result = mockMvc.perform(multipart("/api/files/upload")
                .file(file)
                .header("Authorization", "Bearer " + authToken))
            .andReturn();

        // Se aceitar: verificar que o nome foi sanitizado (UUID gerado)
        if (result.getResponse().getStatus() == 200) {
            String body = result.getResponse().getContentAsString();
            String storedName = objectMapper.readTree(body).get("filename").asText();

            assertThat(storedName).doesNotContain("..");
            assertThat(storedName).doesNotContain("/");
            assertThat(storedName).doesNotContain("\\");
            assertThat(storedName).doesNotContain("passwd");
            assertThat(storedName).doesNotContain("\0");
            // Nome deve ser UUID
            assertThat(storedName).matches("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}.*");
        }
    }

    // ─── Download seguro ───

    @Test
    @DisplayName("Download de arquivo alheio deve retornar 404")
    void dado_downloadArquivoAlheio_entao_404() throws Exception {
        createTestUser("outrousuario@test.com", "Senha@2024");
        String outroToken = authenticateAndGetToken("outrousuario@test.com", "Senha@2024");

        // Fazer upload como outro usuário
        UUID fileId = uploadFileAndGetId(outroToken);

        // Tentar baixar como usuário A
        mockMvc.perform(get("/api/files/" + fileId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Download deve ter Content-Disposition: attachment (não executa no browser)")
    void dado_downloadArquivo_entao_contentDispositionAttachment() throws Exception {
        UUID fileId = uploadFileAndGetId(authToken);

        mockMvc.perform(get("/api/files/" + fileId)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().isOk())
            .andExpect(header().string("Content-Disposition",
                startsWith("attachment;")))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", containsString("no-store")));
    }
}
```

---

## 2. SSRF

```java
// test/.../ssrf/SsrfProtectionTest.java
@DisplayName("SSRF — Server-Side Request Forgery")
@WireMockTest(httpPort = 9999) // servidor externo falso
class SsrfProtectionTest extends BaseSecurityIntegrationTest {

    private String authToken;

    @BeforeEach
    void setup() throws Exception {
        createTestUser("user@test.com", "SenhaForte@2024");
        authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");
    }

    static Stream<Arguments> ssrfPayloads() {
        return Stream.of(
            // AWS IMDS — o mais crítico
            Arguments.of("http://169.254.169.254/latest/meta-data/", "AWS IMDS v1"),
            Arguments.of("http://169.254.169.254/latest/meta-data/iam/security-credentials/",
                         "AWS IAM credentials"),

            // Localhost bypasses
            Arguments.of("http://127.0.0.1:8080/api/admin", "localhost direct"),
            Arguments.of("http://localhost:8080/api/admin", "localhost hostname"),
            Arguments.of("http://[::1]:8080/api/admin", "IPv6 loopback"),
            Arguments.of("http://0177.0.0.1", "octal localhost"),
            Arguments.of("http://2130706433", "decimal localhost"),
            Arguments.of("http://0x7f000001", "hex localhost"),

            // RFC 1918 (redes privadas)
            Arguments.of("http://10.0.0.1:3306", "internal MySQL"),
            Arguments.of("http://192.168.1.1", "private network"),
            Arguments.of("http://172.16.0.1:6379", "internal Redis"),

            // Serviços internos por porta
            Arguments.of("http://db.internal:5432", "PostgreSQL port"),
            Arguments.of("http://cache.internal:6379", "Redis port"),
            Arguments.of("http://elastic.internal:9200/_cat/indices", "Elasticsearch"),

            // Protocolos perigosos
            Arguments.of("file:///etc/passwd", "file protocol"),
            Arguments.of("dict://localhost:6379/", "dict protocol Redis"),
            Arguments.of("gopher://localhost:25/_MAIL", "gopher SMTP")
        );
    }

    @ParameterizedTest(name = "[{index}] {1}")
    @MethodSource("ssrfPayloads")
    @DisplayName("URL suspeita de SSRF deve ser bloqueada")
    void dado_urlSsrf_entao_bloqueado(String maliciousUrl, String desc) throws Exception {
        mockMvc.perform(post("/api/webhooks")
                .header("Authorization", "Bearer " + authToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Meu Webhook",
                    "callbackUrl", maliciousUrl))))
            .andExpect(status().isBadRequest());

        // Verificar que o evento de segurança foi publicado
        await().atMost(2, SECONDS).untilAsserted(() ->
            assertThat(auditLogRepository.findByEventType(SSRF_ATTEMPT)).isNotEmpty()
        );
    }

    @Test
    @DisplayName("URL HTTPS legítima deve ser aceita")
    void dado_urlHttpsExterna_entao_aceita(WireMockRuntimeInfo wm) throws Exception {
        // Configurar WireMock para simular endpoint externo legítimo
        stubFor(post(urlEqualTo("/webhook"))
            .willReturn(aResponse().withStatus(200).withBody("{\"ok\":true}")));

        mockMvc.perform(post("/api/webhooks")
                .header("Authorization", "Bearer " + authToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "name", "Webhook Legítimo",
                    "callbackUrl", "http://localhost:9999/webhook"))))
            .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("URL HTTP (sem S) deve ser rejeitada — apenas HTTPS")
    void dado_urlHttp_entao_rejeitada() throws Exception {
        mockMvc.perform(post("/api/webhooks")
                .header("Authorization", "Bearer " + authToken)
                .contentType(APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "callbackUrl", "http://api.externa.com/webhook")))) // HTTP, não HTTPS
            .andExpect(status().isBadRequest());
    }
}
```

---

## 3. Path Traversal

```java
// test/.../traversal/PathTraversalTest.java
@DisplayName("Path Traversal — Proteção")
class PathTraversalTest extends BaseSecurityIntegrationTest {

    @ParameterizedTest(name = "Payload: {0}")
    @ValueSource(strings = {
        "../../../etc/passwd",
        "..%2F..%2F..%2Fetc%2Fpasswd",
        "%252e%252e%252f%252e%252e%252f",  // double URL encoding
        "....//....//etc/passwd",
        "..\\..\\windows\\system32",
        "%2e%2e/%2e%2e/etc/passwd",        // single URL encoding
        "/etc/passwd%00.jpg",              // null byte
        "~/../../../etc/shadow"            // tilde traversal
    })
    @DisplayName("Path traversal em parâmetro de arquivo deve ser bloqueado")
    void dado_pathTraversal_entao_bloqueado(String maliciousPath) throws Exception {
        String authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        mockMvc.perform(get("/api/files/download")
                .param("path", maliciousPath)
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().is4xxClientError());

        // Verificar que o evento foi logado
        await().atMost(2, SECONDS).untilAsserted(() ->
            assertThat(auditLogRepository.findByEventType(PATH_TRAVERSAL_ATTEMPT)).isNotEmpty()
        );
    }

    @Test
    @DisplayName("Arquivo fora do diretório base não pode ser acessado mesmo com path válido")
    void dado_pathForaDoBaseDir_entao_bloqueado() throws Exception {
        String authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        // Path que parece válido mas resolve fora do base dir
        mockMvc.perform(get("/api/files/download")
                .param("path", "subdir/../../../../../../etc/hosts")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().is4xxClientError());
    }

    @Test
    @DisplayName("Symlink para fora do diretório base não deve ser seguido")
    void dado_symlinkParaForaDoBaseDir_entao_bloqueado(@TempDir Path tempDir) throws Exception {
        // Criar symlink apontando para /etc/passwd
        Path symlink = tempDir.resolve("malicious-symlink.txt");
        Files.createSymbolicLink(symlink, Path.of("/etc/passwd"));

        String authToken = authenticateAndGetToken("user@test.com", "SenhaForte@2024");

        mockMvc.perform(get("/api/files/download")
                .param("path", "malicious-symlink.txt")
                .header("Authorization", "Bearer " + authToken))
            .andExpect(status().is4xxClientError());
    }
}
```
