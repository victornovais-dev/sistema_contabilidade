# Layer 2 — Rate Limiting e Anti-Brute Force

## Fingerprint Multi-Sinal

Não confiar apenas no IP — combinar múltiplos sinais para identificar o cliente:

```java
// utils/FingerprintService.java
@Service
public class FingerprintService {

    public String generateFingerprint(HttpServletRequest request) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put("user_agent", getHeader(request, "User-Agent"));
        data.put("accept_language", getHeader(request, "Accept-Language"));
        data.put("accept_encoding", getHeader(request, "Accept-Encoding"));
        data.put("accept", getHeader(request, "Accept"));
        data.put("sec_ch_ua", getHeader(request, "Sec-CH-UA"));
        data.put("sec_ch_platform", getHeader(request, "Sec-CH-Platform"));
        data.put("sec_fetch_site", getHeader(request, "Sec-Fetch-Site"));
        data.put("ip_subnet", getIpSubnet(request)); // /24 para agrupar redes

        try {
            String json = new ObjectMapper().writeValueAsString(data);
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(json.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            return getClientIp(request); // fallback para IP puro
        }
    }

    private String getIpSubnet(HttpServletRequest request) {
        String ip = getClientIp(request);
        // Retorna apenas os 3 primeiros octetos (/24)
        String[] parts = ip.split("\\.");
        if (parts.length == 4) {
            return parts[0] + "." + parts[1] + "." + parts[2] + ".0/24";
        }
        return ip;
    }

    public String getClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String getHeader(HttpServletRequest request, String name) {
        String value = request.getHeader(name);
        return value != null ? value : "";
    }
}
```

---

## Limites por Endpoint (IP + Fingerprint combinados)

| Endpoint | Limite | Janela | Lockout |
|----------|--------|--------|---------|
| `/api/auth/login` | 5 tentativas | 15 min | Progressivo: 15min → 1h → 24h |
| `/api/auth/register` | 3 cadastros | 1h | Bloquear IP/subnet |
| `/api/auth/recover` | 3 tentativas | 1h | Silencioso |
| `/api/posts` | 20 criações | 1h | Soft block 30min |
| `/api/messages` | 60 mensagens | 1h | Soft block 30min |
| `/api/comments` | 30 comentários | 1h | Soft block 30min |
| `/api/**` (genérico) | 300 requests | 1 min | Hard block 1h |

---

## Implementação com Bucket4j + Redis

```java
// config/RateLimitConfig.java
@Configuration
public class RateLimitConfig {

    @Bean
    public ProxyManager<String> bucketProxyManager(RedissonClient redissonClient) {
        return Bucket4jRedisson.casBasedBuilder(redissonClient).build();
    }
}
```

```java
// service/RateLimitService.java
@Service
public class RateLimitService {

    @Autowired
    private ProxyManager<String> bucketProxyManager;

    @Autowired
    private FingerprintService fingerprintService;

    // Definições de limite por endpoint
    private static final Map<String, BandwidthDefinition> ENDPOINT_LIMITS = Map.of(
        "/api/auth/login",    new BandwidthDefinition(5, Duration.ofMinutes(15)),
        "/api/auth/register", new BandwidthDefinition(3, Duration.ofHours(1)),
        "/api/auth/recover",  new BandwidthDefinition(3, Duration.ofHours(1)),
        "/api/posts",         new BandwidthDefinition(20, Duration.ofHours(1)),
        "/api/messages",      new BandwidthDefinition(60, Duration.ofHours(1)),
        "/api/comments",      new BandwidthDefinition(30, Duration.ofHours(1))
    );

    // Limite genérico para qualquer /api/**
    private static final BandwidthDefinition GENERIC_LIMIT =
        new BandwidthDefinition(300, Duration.ofMinutes(1));

    public boolean isAllowed(HttpServletRequest request) {
        String endpoint = getNormalizedPath(request.getRequestURI());
        String fingerprint = fingerprintService.generateFingerprint(request);
        String ip = fingerprintService.getClientIp(request);

        // Chave combina IP + fingerprint
        String bucketKey = "rl:" + endpoint + ":" + ip + ":" + fingerprint.substring(0, 16);

        BandwidthDefinition def = ENDPOINT_LIMITS.getOrDefault(endpoint, GENERIC_LIMIT);

        BucketConfiguration config = BucketConfiguration.builder()
            .addLimit(limit -> limit
                .capacity(def.capacity())
                .refillGreedy(def.capacity(), def.window()))
            .build();

        Bucket bucket = bucketProxyManager.builder().build(bucketKey, config);
        return bucket.tryConsume(1);
    }

    private String getNormalizedPath(String uri) {
        // Normalizar /api/posts/uuid → /api/posts
        return uri.replaceAll("/[0-9a-fA-F\\-]{36}.*$", "")
                  .replaceAll("/[0-9]+.*$", "");
    }

    record BandwidthDefinition(long capacity, Duration window) {}
}
```

---

## Lockout Progressivo para Login

```java
// service/LoginAttemptService.java
@Service
public class LoginAttemptService {

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final int[] LOCKOUT_MINUTES = {15, 60, 1440}; // 15min, 1h, 24h

    public boolean isLockedOut(String identifier) {
        String lockKey = "lockout:" + identifier;
        return Boolean.TRUE.equals(redisTemplate.hasKey(lockKey));
    }

    public void registerFailedAttempt(String identifier) {
        String attemptsKey = "attempts:" + identifier;
        Long attempts = redisTemplate.opsForValue().increment(attemptsKey);
        redisTemplate.expire(attemptsKey, Duration.ofHours(24));

        if (attempts != null && attempts >= 5) {
            applyProgressiveLockout(identifier, attempts.intValue());
        }
    }

    private void applyProgressiveLockout(String identifier, int attempts) {
        // Calcular nível de lockout (0, 1, 2)
        int level = Math.min((attempts / 5) - 1, LOCKOUT_MINUTES.length - 1);
        int lockoutMinutes = LOCKOUT_MINUTES[level];

        String lockKey = "lockout:" + identifier;
        redisTemplate.opsForValue().set(lockKey, String.valueOf(lockoutMinutes));
        redisTemplate.expire(lockKey, Duration.ofMinutes(lockoutMinutes));
    }

    public void resetAttempts(String identifier) {
        redisTemplate.delete("attempts:" + identifier);
        redisTemplate.delete("lockout:" + identifier);
    }
}
```

---

## Lockout Silencioso — Sempre HTTP 200

Após lockout, **nunca revelar** que o IP está bloqueado:

```java
// filter/RateLimitFilter.java
@Component
@Order(1)
public class RateLimitFilter extends OncePerRequestFilter {

    @Autowired
    private RateLimitService rateLimitService;

    @Autowired
    private LoginAttemptService loginAttemptService;

    private static final List<String> SILENT_LOCKOUT_ENDPOINTS = List.of(
        "/api/auth/login", "/api/auth/recover"
    );

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain)
            throws ServletException, IOException {

        String path = request.getRequestURI();
        String ip = fingerprintService.getClientIp(request);

        // Verificar lockout de login
        if (path.startsWith("/api/auth/login") && loginAttemptService.isLockedOut(ip)) {
            sendSilentLockoutResponse(response, path);
            return;
        }

        // Rate limit geral
        if (!rateLimitService.isAllowed(request)) {
            if (isSilentEndpoint(path)) {
                sendSilentLockoutResponse(response, path);
            } else {
                response.setStatus(HttpServletResponse.SC_OK); // soft block também retorna 200
                response.setContentType("application/json");
                response.getWriter().write("{\"message\":\"Aguarde antes de tentar novamente\"}");
            }
            return;
        }

        chain.doFilter(request, response);
    }

    private void sendSilentLockoutResponse(HttpServletResponse response, String path)
            throws IOException {
        // SEMPRE HTTP 200 com mensagem genérica — não revelar lockout
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/json");

        if (path.contains("/login")) {
            response.getWriter().write("{\"error\":\"Credenciais inválidas\"}");
        } else {
            response.getWriter().write("{\"message\":\"Se o email existir, você receberá instruções\"}");
        }
    }

    private boolean isSilentEndpoint(String path) {
        return SILENT_LOCKOUT_ENDPOINTS.stream().anyMatch(path::startsWith);
    }
}
```

---

## Checklist Layer 2

- [ ] Fingerprint multi-sinal implementado (não só IP)
- [ ] Limites definidos por endpoint com Bucket4j + Redis
- [ ] Lockout progressivo para `/login`: 15min → 1h → 24h
- [ ] Após lockout: sempre HTTP 200 com resposta genérica (silencioso)
- [ ] `/recover` usa lockout silencioso
- [ ] Chave de rate limit combina IP + fingerprint
- [ ] Soft block para endpoints de conteúdo (posts, messages, comments)
- [ ] Hard block para `/api/**` genérico
