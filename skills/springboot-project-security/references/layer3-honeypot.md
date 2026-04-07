# Layer 3 — Honeypots e Deception

## Conceito

Rotas honeypot são endpoints falsos que nenhum usuário legítimo jamais acessaria.
Qualquer acesso é **automaticamente suspeito** — registrar silenciosamente e
retornar HTTP 200 com JSON falso para não alertar o atacante.

---

## Rotas Honeypot no Backend

Registrar silenciosamente: IP, User-Agent, headers completos, body, timestamp, rota.

```
/admin          /dashboard       /cms            /painel
/wp-admin       /wp-login.php    /administrator  /login-admin
/api/admin      /api/v1/admin    /api/v2/admin   /phpmyadmin
/config         /config.php      /.env           /.git
/backup         /db              /database       /setup
/install        /shell           /cmd            /exec
/eval           /api/users*      /api/debug      /api/test
/swagger        /api-docs        /graphql        /actuator
/metrics        /health/details  /env            /heapdump
```

> *`/api/users` sem autenticação retorna lista falsa de usuários.*

---

## Entidade de Log

```java
// entity/HoneypotLog.java
@Entity
@Table(name = "honeypot_logs")
public class HoneypotLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String ipAddress;

    @Column(nullable = false)
    private String route;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    @Column(columnDefinition = "TEXT")
    private String headersJson; // todos os headers

    @Column(columnDefinition = "TEXT")
    private String requestBody;

    @Column(nullable = false)
    private String httpMethod;

    @Column(nullable = false)
    private Instant timestamp;

    private String fingerprint;
}
```

---

## Respostas Falsas para Confundir Atacantes

```java
// config/HoneypotResponses.java
public class HoneypotResponses {

    // Retornar aleatoriamente para dificultar pattern matching
    public static final List<Map<String, Object>> FAKE_RESPONSES = List.of(
        Map.of("status", "ok", "data", List.of()),
        Map.of("success", true, "token", "eyJ...falso..." + generateFakeSuffix()),
        Map.of("users", List.of(), "total", 0, "page", 1),
        Map.of("message", "Carregando...", "progress", 23),
        Map.of("error", false, "result", "OK", "timestamp", System.currentTimeMillis()),
        Map.of("version", "2.1.0", "status", "running", "uptime", 99842)
    );

    private static String generateFakeSuffix() {
        return Base64.getEncoder().encodeToString(
            UUID.randomUUID().toString().getBytes()
        ).substring(0, 12);
    }

    public static Map<String, Object> getRandom() {
        int index = new SecureRandom().nextInt(FAKE_RESPONSES.size());
        return FAKE_RESPONSES.get(index);
    }
}
```

---

## Honeypot Filter

```java
// filter/HoneypotFilter.java
@Component
@Order(0) // Executar ANTES de qualquer outro filtro
public class HoneypotFilter extends OncePerRequestFilter {

    @Autowired
    private HoneypotLogRepository honeypotLogRepository;

    @Autowired
    private FingerprintService fingerprintService;

    @Autowired
    private ObjectMapper objectMapper;

    // Lista completa de rotas honeypot
    private static final Set<String> HONEYPOT_PATHS = Set.of(
        "/admin", "/dashboard", "/cms", "/painel",
        "/wp-admin", "/wp-login.php", "/administrator", "/login-admin",
        "/api/admin", "/api/v1/admin", "/api/v2/admin", "/phpmyadmin",
        "/config", "/config.php", "/.env", "/.git",
        "/backup", "/db", "/database", "/setup",
        "/install", "/shell", "/cmd", "/exec",
        "/eval", "/api/debug", "/api/test",
        "/swagger", "/swagger-ui.html", "/api-docs", "/graphql",
        "/actuator", "/actuator/env", "/actuator/heapdump",
        "/metrics", "/health/details"
    );

    // Prefixos honeypot (qualquer path que começa com estes)
    private static final List<String> HONEYPOT_PREFIXES = List.of(
        "/admin/", "/wp-", "/phpmyadmin", "/actuator/"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI().toLowerCase();

        if (isHoneypotPath(path)) {
            logHoneypotAccess(request, path);
            sendFakeResponse(response);
            return; // NÃO continuar a chain
        }

        chain.doFilter(request, response);
    }

    private boolean isHoneypotPath(String path) {
        if (HONEYPOT_PATHS.contains(path)) return true;
        return HONEYPOT_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private void logHoneypotAccess(HttpServletRequest request, String path) {
        try {
            HoneypotLog log = new HoneypotLog();
            log.setIpAddress(fingerprintService.getClientIp(request));
            log.setRoute(path);
            log.setUserAgent(request.getHeader("User-Agent"));
            log.setHttpMethod(request.getMethod());
            log.setTimestamp(Instant.now());
            log.setFingerprint(fingerprintService.generateFingerprint(request));

            // Capturar todos os headers
            Map<String, String> headers = new HashMap<>();
            Collections.list(request.getHeaderNames())
                .forEach(name -> headers.put(name, request.getHeader(name)));
            log.setHeadersJson(objectMapper.writeValueAsString(headers));

            // Capturar body (com limite de tamanho)
            try {
                String body = request.getReader().lines()
                    .collect(Collectors.joining())
                    .substring(0, Math.min(2000, /* body length */ 2000));
                log.setRequestBody(body);
            } catch (Exception ignored) {}

            // Salvar assincronamente para não bloquear a resposta
            CompletableFuture.runAsync(() -> honeypotLogRepository.save(log));

            // Log de segurança
            log.warn("🍯 HONEYPOT HIT: ip={} path={} ua={}",
                log.getIpAddress(), path, log.getUserAgent());

        } catch (Exception e) {
            // Nunca deixar um erro de log vazar para a resposta
        }
    }

    private void sendFakeResponse(HttpServletResponse response) throws IOException {
        // Simular delay realista (50-200ms) para parecer real
        try {
            long delay = 50 + new SecureRandom().nextInt(150);
            Thread.sleep(delay);
        } catch (InterruptedException ignored) {}

        response.setStatus(HttpServletResponse.SC_OK); // SEMPRE 200
        response.setContentType("application/json");
        response.setHeader("X-Powered-By", "PHP/7.4.1"); // header falso para confundir

        Map<String, Object> fakeResponse = HoneypotResponses.getRandom();
        response.getWriter().write(new ObjectMapper().writeValueAsString(fakeResponse));
    }
}
```

---

## Rota Admin Real — Segurança Máxima

A rota admin real **nunca deve ser escrita em código**:

```java
// filter/AdminRouteFilter.java
@Component
public class AdminRouteFilter extends OncePerRequestFilter {

    @Value("${admin.route.secret:}") // NUNCA ter valor default em prod
    private String adminRouteSecret;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();

        // Verificar se é tentativa de acesso ao painel admin
        if (path.startsWith("/admin-") || path.contains("manage")) {
            // Rota secreta via variável de ambiente — nunca hardcoded
            String expectedPath = "/api/" + adminRouteSecret + "/admin";

            if (!path.equals(expectedPath)) {
                // Tratar como honeypot — não revelar que a rota existe
                logHoneypotAccess(request, path);
                sendFakeResponse(response);
                return;
            }
        }

        chain.doFilter(request, response);
    }
}
```

**Proteções na rota admin real:**
1. Rota secreta via `ADMIN_ROUTE_SECRET` (env var) — nunca em código ou logs
2. JWT válido com role `ADMIN`
3. 2FA verificado (TOTP)
4. IP allowlist opcional (`ADMIN_IP_ALLOWLIST`)

```java
// security/AdminSecurityConfig.java
@Configuration
public class AdminSecurityConfig {

    @Value("${admin.ip.allowlist:}") // opcional
    private String[] adminIpAllowlist;

    public boolean isAdminIpAllowed(String ip) {
        if (adminIpAllowlist == null || adminIpAllowlist.length == 0) {
            return true; // allowlist desabilitada
        }
        return Arrays.asList(adminIpAllowlist).contains(ip);
    }
}
```

---

## Alertas de Segurança

```java
// service/SecurityAlertService.java
@Service
public class SecurityAlertService {

    @Autowired
    private JavaMailSender mailSender;

    @Value("${security.alert.email}")
    private String alertEmail;

    // Enviar alerta se mesmo IP/fingerprint atingir múltiplos honeypots
    @Scheduled(fixedDelay = 300_000) // a cada 5 minutos
    public void checkHoneypotPatterns() {
        Instant since = Instant.now().minus(1, ChronoUnit.HOURS);
        List<HoneypotSummary> suspicious = honeypotLogRepository
            .findRepeatOffenders(since, 3); // 3+ hits em 1h

        suspicious.forEach(s -> sendSecurityAlert(
            "Atacante detectado: IP " + s.ip() + " — " + s.hitCount() + " honeypots em 1h"
        ));
    }
}
```

---

## Checklist Layer 3

- [ ] `HoneypotFilter` com `@Order(0)` — executa antes de tudo
- [ ] Todos os paths honeypot registrados (mínimo: wp-admin, .env, .git, admin, phpmyadmin)
- [ ] Respostas honeypot: sempre HTTP 200 com JSON falso e aleatório
- [ ] Logs capturam: IP, path, user-agent, headers completos, body, timestamp
- [ ] Rota admin real definida via variável de ambiente `ADMIN_ROUTE_SECRET`
- [ ] Rota admin nunca aparece em código, logs ou respostas de erro
- [ ] Alertas automáticos para atacantes recorrentes
- [ ] Headers falsos opcionais (`X-Powered-By`) para desorientar
