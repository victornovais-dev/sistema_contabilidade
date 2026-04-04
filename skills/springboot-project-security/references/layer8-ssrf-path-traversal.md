# Layer 8 — Proteção contra SSRF e Path Traversal

> **Mindset pentester:** SSRF é o ataque favorito de pentesters contra cloud.
> `http://169.254.169.254/latest/meta-data/` no AWS retorna credenciais IAM em texto puro.
> Path traversal com `../../../etc/passwd` é clássico mas ainda derruba sistemas em 2025.
> Ambos são triviais de explorar e devastadores.

---

## SSRF — Server-Side Request Forgery

### Cenários de ataque

```
# AWS IMDS v1 (sem autenticação)
url = "http://169.254.169.254/latest/meta-data/iam/security-credentials/prod-role"
# Retorna: AccessKeyId, SecretAccessKey, Token

# Serviços internos — inacessíveis externamente mas acessíveis via SSRF
url = "http://internal-db.corp:5432"
url = "http://redis.internal:6379"
url = "http://elasticsearch.internal:9200/_cat/indices"

# Localhost bypass
url = "http://127.0.0.1:8080/api/admin/users"
url = "http://[::1]:8080/api/admin/users"   # IPv6 localhost
url = "http://0177.0.0.1"                   # octal
url = "http://2130706433"                    # decimal (127.0.0.1)
url = "http://0x7f000001"                    # hex

# DNS rebinding — domínio que resolve para IP interno
url = "http://attacker-controlled.com"      # resolve para 192.168.1.1
```

### Validação de URL contra SSRF

```java
// utils/SsrfValidator.java
@Component
public class SsrfValidator {

    // Ranges de IP privados e reservados — NUNCA fazer requests para estes
    private static final List<CidrRange> BLOCKED_RANGES = List.of(
        new CidrRange("127.0.0.0/8"),       // localhost
        new CidrRange("10.0.0.0/8"),        // RFC 1918
        new CidrRange("172.16.0.0/12"),     // RFC 1918
        new CidrRange("192.168.0.0/16"),    // RFC 1918
        new CidrRange("169.254.0.0/16"),    // link-local (AWS IMDS!)
        new CidrRange("100.64.0.0/10"),     // shared address space
        new CidrRange("::1/128"),           // IPv6 loopback
        new CidrRange("fc00::/7"),          // IPv6 unique local
        new CidrRange("fe80::/10"),         // IPv6 link-local
        new CidrRange("0.0.0.0/8"),        // "this" network
        new CidrRange("240.0.0.0/4")        // reservado
    );

    // Apenas protocolos explicitamente permitidos
    private static final Set<String> ALLOWED_PROTOCOLS = Set.of("https");

    // Allowlist de domínios externos permitidos (se aplicável)
    @Value("${ssrf.allowed-domains:}")
    private String allowedDomainsConfig;

    public void validateUrl(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            throw new InvalidInputException("URL inválida");
        }

        URL url;
        try {
            url = new URL(rawUrl);
        } catch (MalformedURLException e) {
            throw new InvalidInputException("URL malformada");
        }

        // 1. Verificar protocolo
        if (!ALLOWED_PROTOCOLS.contains(url.getProtocol().toLowerCase())) {
            throw new SsrfBlockedException("Protocolo não permitido: " + url.getProtocol());
        }

        // 2. Bloquear endereços IP diretamente na URL (bypass de DNS)
        String host = url.getHost();
        if (isIpAddress(host)) {
            validateIpNotBlocked(host);
        }

        // 3. Resolver DNS AGORA e verificar o IP resultante
        // (protege contra DNS rebinding — o IP pode mudar entre a validação e o request)
        InetAddress[] resolved;
        try {
            resolved = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("Host não resolvível: " + host);
        }

        for (InetAddress addr : resolved) {
            if (isBlockedIp(addr)) {
                SecurityEventPublisher.publish(SecurityEvent.SSRF_ATTEMPT,
                    "URL: " + rawUrl + " → IP: " + addr.getHostAddress());
                throw new SsrfBlockedException("Destino não permitido");
            }
        }

        // 4. Verificar contra allowlist de domínios (se configurada)
        if (!allowedDomainsConfig.isBlank()) {
            Set<String> allowed = Set.of(allowedDomainsConfig.split(","));
            boolean domainAllowed = allowed.stream()
                .anyMatch(d -> host.equals(d.trim()) || host.endsWith("." + d.trim()));
            if (!domainAllowed) {
                throw new SsrfBlockedException("Domínio não está na allowlist");
            }
        }

        // 5. Porta suspeita — bloquear portas de serviços internos
        int port = url.getPort();
        if (port > 0 && isSensitivePort(port)) {
            throw new SsrfBlockedException("Porta não permitida: " + port);
        }
    }

    private boolean isIpAddress(String host) {
        try {
            InetAddress addr = InetAddress.getByName(host);
            return addr.getHostAddress().equals(host);
        } catch (Exception e) {
            return false;
        }
    }

    private void validateIpNotBlocked(String ipStr) {
        try {
            InetAddress addr = InetAddress.getByName(ipStr);
            if (isBlockedIp(addr)) {
                throw new SsrfBlockedException("IP não permitido");
            }
        } catch (UnknownHostException e) {
            throw new SsrfBlockedException("IP inválido");
        }
    }

    private boolean isBlockedIp(InetAddress addr) {
        if (addr.isLoopbackAddress()) return true;
        if (addr.isSiteLocalAddress()) return true;
        if (addr.isLinkLocalAddress()) return true;
        if (addr.isAnyLocalAddress()) return true;
        if (addr.isMulticastAddress()) return true;

        // Verificar ranges CIDR adicionais
        return BLOCKED_RANGES.stream().anyMatch(range -> range.contains(addr));
    }

    private boolean isSensitivePort(int port) {
        Set<Integer> sensitivePorts = Set.of(
            22, 23, 25, 110, 143,  // SSH, Telnet, SMTP, POP3, IMAP
            3306, 5432, 27017, 6379, 9200, // DB ports
            2375, 2376, 8500, 8200,  // Docker, Consul, Vault
            4040, 8888, 9090         // Jupyter, Prometheus
        );
        return sensitivePorts.contains(port);
    }
}
```

### HttpClient com SSRF Protection Embutida

```java
// config/SafeHttpClientConfig.java
@Configuration
public class SafeHttpClientConfig {

    @Autowired
    private SsrfValidator ssrfValidator;

    @Bean
    public RestTemplate safeRestTemplate() {
        // Custom ClientHttpRequestFactory que valida SSRF antes de cada request
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory() {
            @Override
            public ClientHttpRequest createRequest(URI uri, HttpMethod httpMethod)
                    throws IOException {
                // Validar contra SSRF ANTES de abrir conexão
                ssrfValidator.validateUrl(uri.toString());

                // Timeout agressivo — evitar slow loris
                setConnectTimeout(3000);   // 3s para conectar
                setReadTimeout(10000);     // 10s para ler resposta

                return super.createRequest(uri, httpMethod);
            }
        };

        RestTemplate template = new RestTemplate(factory);

        // Não seguir redirects — podem redirecionar para IPs internos
        factory.setBufferRequestBody(false);

        return template;
    }
}
```

---

## Path Traversal

> **Vetores reais:** `../../../etc/passwd`, URL encoding `%2e%2e/`, double encoding `%252e%252e/`,
> null byte `file.txt%00.jpg`, unicode `..%c0%af../`, Windows UNC `\\server\share`

```java
// utils/PathTraversalGuard.java
@Component
public class PathTraversalGuard {

    /**
     * Valida que o path final está dentro do diretório base permitido.
     * Resolve o path canônico DEPOIS de normalizar — evita todos os vetores conhecidos.
     */
    public Path safeResolve(Path baseDir, String userInput) {
        if (userInput == null || userInput.isBlank()) {
            throw new InvalidInputException("Path inválido");
        }

        // 1. Decodificar URL encoding múltiplas vezes (double encoding bypass)
        String decoded = userInput;
        for (int i = 0; i < 3; i++) {
            try {
                String prev = decoded;
                decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (decoded.equals(prev)) break;
            } catch (Exception ignored) { break; }
        }

        // 2. Remover null bytes (null byte injection)
        decoded = decoded.replace("\0", "");

        // 3. Bloquear padrões óbvios antes mesmo de resolver
        if (decoded.contains("..") || decoded.contains("~") ||
            decoded.startsWith("/") || decoded.contains(":\\") ||
            decoded.startsWith("//") || decoded.startsWith("\\\\")) {
            SecurityEventPublisher.publish(SecurityEvent.PATH_TRAVERSAL_ATTEMPT, decoded);
            throw new InvalidInputException("Path inválido");
        }

        // 4. Resolver canonicamente — o método definitivo
        try {
            Path resolved = baseDir.resolve(decoded).normalize();
            Path canonical = resolved.toRealPath(LinkOption.NOFOLLOW_LINKS);
            Path canonicalBase = baseDir.toRealPath(LinkOption.NOFOLLOW_LINKS);

            // A única verificação que realmente importa
            if (!canonical.startsWith(canonicalBase)) {
                SecurityEventPublisher.publish(SecurityEvent.PATH_TRAVERSAL_ATTEMPT,
                    "Attempted escape: " + decoded + " → " + canonical);
                throw new SecurityException("Acesso negado");
            }

            return canonical;
        } catch (IOException e) {
            throw new ResourceNotFoundException();
        }
    }

    /**
     * Validação para parâmetros de arquivo em APIs
     */
    public void assertSafeFilename(String filename) {
        if (filename == null) throw new InvalidInputException("Nome inválido");

        // Detectar tentativas de traversal em nomes de arquivo
        List<String> dangerous = List.of(
            "../", "..\\", "%2e%2e", "%252e%252e",
            "\0", "%00", "~", "/etc/", "/proc/",
            "C:\\", "\\\\", "UNC\\"
        );

        String lower = filename.toLowerCase();
        for (String d : dangerous) {
            if (lower.contains(d.toLowerCase())) {
                SecurityEventPublisher.publish(SecurityEvent.PATH_TRAVERSAL_ATTEMPT, filename);
                throw new InvalidInputException("Nome de arquivo inválido");
            }
        }
    }
}
```

---

## Uso nos Controllers

```java
// Validar URL de webhook/integração externa
@PostMapping("/webhooks")
public ResponseEntity<?> createWebhook(@Valid @RequestBody WebhookRequest req) {
    ssrfValidator.validateUrl(req.getCallbackUrl()); // ← SSRF check
    return webhookService.create(req);
}

// Validar acesso a arquivo
@GetMapping("/files/{filename}")
public ResponseEntity<Resource> getFile(@PathVariable String filename) {
    Path baseDir = Paths.get(System.getenv("UPLOAD_BASE_PATH"));
    Path safePath = pathTraversalGuard.safeResolve(baseDir, filename); // ← Path check
    return serveFile(safePath);
}
```

---

## Checklist Layer 8

- [ ] `SsrfValidator` aplicado em TODA URL fornecida pelo usuário
- [ ] Bloqueio de IPs privados/reservados: 127.x, 10.x, 172.16-31.x, 192.168.x, 169.254.x
- [ ] DNS resolvido na validação — não confiar apenas no hostname (DNS rebinding)
- [ ] Apenas HTTPS permitido — sem file://, gopher://, dict://
- [ ] Portas de serviços internos bloqueadas (3306, 5432, 6379, etc.)
- [ ] Timeout agressivo no HTTP client (3s connect, 10s read)
- [ ] Redirects não seguidos automaticamente
- [ ] Path traversal: decodificar múltiplas vezes antes de validar
- [ ] `Path.toRealPath(NOFOLLOW_LINKS)` + `startsWith(baseDir)` como verificação definitiva
- [ ] Null bytes removidos de nomes de arquivo
- [ ] Symlinks não seguidos (`NOFOLLOW_LINKS`)
- [ ] Eventos de segurança publicados para tentativas SSRF/traversal
