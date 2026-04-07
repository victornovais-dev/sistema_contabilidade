# Layer 6 — Rota Admin Invisível + JWT Apenas em Memória

> **Mindset pentester:** Toda rota admin exposta é um alvo. Toda rota com nome previsível
> é um alvo. JWT em localStorage é equivalente a escrever o token no localStorage do
> atacante — qualquer XSS o lê instantaneamente. Você não tem XSS? Ainda não encontrou.

---

## Rota Admin Completamente Invisível

### Estratégia: A rota não existe até você provar que é admin

```java
// filter/AdminRouteFilter.java
@Component @Order(0) // Antes de tudo — incluindo Spring Security
public class AdminRouteFilter extends OncePerRequestFilter {

    @Value("${admin.route.secret}") // NUNCA tem valor default
    private String adminRouteSecret;

    @Value("${admin.ip.allowlist:}") // opcional
    private String adminIpAllowlist;

    @Autowired private FingerprintService fingerprintService;
    @Autowired private HoneypotLogRepository honeypotLogRepository;

    // A rota admin real é /api/{SECRET_HASH}/manage — nunca hardcoded
    // Calculada em runtime a partir da variável de ambiente

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        String path = req.getRequestURI();

        // Se o path parece ser tentativa de acesso admin mas não é a rota secreta real
        if (looksLikeAdminAccess(path) && !isRealAdminRoute(path)) {
            // Registrar como honeypot — atacante não sabe que foi detectado
            logSuspiciousAccess(req, path);
            sendHoneypotResponse(res);
            return;
        }

        // É a rota admin real — verificar IP allowlist antes de qualquer outra coisa
        if (isRealAdminRoute(path)) {
            String clientIp = fingerprintService.getClientIp(req);
            if (!isIpAllowed(clientIp)) {
                // Silencioso — não revelar que a rota existe para este IP
                logSuspiciousAccess(req, path);
                sendHoneypotResponse(res);
                return;
            }
        }

        chain.doFilter(req, res);
    }

    private boolean looksLikeAdminAccess(String path) {
        List<String> adminKeywords = List.of(
            "admin", "manage", "dashboard", "panel", "backoffice",
            "cms", "control", "painel", "gerenciar", "moderator"
        );
        String lower = path.toLowerCase();
        return adminKeywords.stream().anyMatch(lower::contains);
    }

    private boolean isRealAdminRoute(String path) {
        // Rota real: /api/{HMAC_DO_SECRET}/manage
        String expectedPath = "/api/" + computeRouteHash() + "/manage";
        return path.startsWith(expectedPath);
    }

    private String computeRouteHash() {
        // Hash do secret para não expor o valor raw na URL
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(adminRouteSecret.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding()
                .encodeToString(hash).substring(0, 16); // 16 chars da hash
        } catch (Exception e) {
            throw new RuntimeException("Falha ao calcular rota admin", e);
        }
    }

    private boolean isIpAllowed(String ip) {
        if (adminIpAllowlist == null || adminIpAllowlist.isBlank()) return true;
        return Arrays.asList(adminIpAllowlist.split(",")).contains(ip.trim());
    }
}
```

### Proteção da Rota Admin Real — 5 Camadas de Verificação

```java
// security/AdminSecurityChain.java
@Component
public class AdminSecurityChain {

    @Autowired private JwtService jwtService;
    @Autowired private TotpService totpService;
    @Autowired private RefreshTokenRepository tokenRepo;

    /**
     * Para acessar qualquer endpoint admin, o request deve passar por:
     * 1. Rota secreta (verificada no AdminRouteFilter)
     * 2. IP allowlist (verificada no AdminRouteFilter)
     * 3. JWT válido com role ADMIN
     * 4. 2FA TOTP verificado (código de 30s)
     * 5. Session binding — token deve ser do mesmo device que fez login
     */
    public AdminContext verifyAdminAccess(HttpServletRequest req) {
        // Layer 3: JWT com role ADMIN
        String token = extractBearerToken(req);
        Claims claims = jwtService.validateAndExtractClaims(token);

        if (!"ADMIN".equals(claims.get("role", String.class))) {
            throw new AccessDeniedException("Acesso negado");
        }

        // Layer 4: 2FA obrigatório para admin
        String totpCode = req.getHeader("X-TOTP-Code");
        UUID userId = UUID.fromString(claims.getSubject());

        if (!totpService.verify(userId, totpCode)) {
            SecurityEventPublisher.publish(SecurityEvent.ADMIN_2FA_FAILED, userId.toString());
            throw new AccessDeniedException("2FA inválido");
        }

        // Layer 5: Session binding — device fingerprint deve bater com o do login
        String currentFingerprint = fingerprintService.generateFingerprint(req);
        String tokenFingerprint = claims.get("deviceFingerprint", String.class);

        if (tokenFingerprint != null && !SecurityUtils.safeEquals(currentFingerprint, tokenFingerprint)) {
            SecurityEventPublisher.publish(SecurityEvent.SESSION_HIJACK_ATTEMPT, userId.toString());
            throw new AccessDeniedException("Sessão inválida");
        }

        return new AdminContext(userId, claims);
    }
}
```

---

## JWT Apenas em Memória — Nunca em Storage

> **Por que localStorage/sessionStorage são vetores de ataque:**
> - Qualquer XSS lê `localStorage.getItem('token')` instantaneamente
> - Extensions maliciosas de browser acessam storage
> - Ferramenta de devtools — usuário pode copiar e o token pode ser exfiltrado
> - Logs de analytics, erros, service workers podem capturar acidentalmente

### Estratégia no Frontend (Angular/React)

```typescript
// auth.service.ts — Token NUNCA toca o DOM storage
class AuthService {
    // Token vive APENAS em variável de módulo (memória JS)
    private accessToken: string | null = null;
    private tokenExpiry: number | null = null;

    // Referência ao timer de renovação automática
    private refreshTimer: ReturnType<typeof setTimeout> | null = null;

    async login(credentials: LoginDto): Promise<void> {
        const response = await fetch('/api/auth/login', {
            method: 'POST',
            credentials: 'include',  // enviar/receber cookies HttpOnly
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(credentials)
        });

        const data = await response.json();

        // Access token apenas em memória
        this.accessToken = data.accessToken;
        this.tokenExpiry = Date.now() + (14 * 60 * 1000); // 14min (1min antes de expirar)

        // Agendar renovação automática antes de expirar
        this.scheduleTokenRefresh();

        // NUNCA: localStorage.setItem('token', data.accessToken)
        // NUNCA: sessionStorage.setItem('token', data.accessToken)
        // NUNCA: document.cookie = 'token=' + data.accessToken
    }

    private scheduleTokenRefresh(): void {
        if (this.refreshTimer) clearTimeout(this.refreshTimer);

        const timeUntilRefresh = (this.tokenExpiry! - Date.now()) - 60_000; // 1min antes

        this.refreshTimer = setTimeout(async () => {
            await this.refreshAccessToken();
        }, Math.max(timeUntilRefresh, 0));
    }

    async refreshAccessToken(): Promise<void> {
        // Refresh token vem do cookie HttpOnly automaticamente
        const response = await fetch('/api/auth/refresh', {
            method: 'POST',
            credentials: 'include' // cookie HttpOnly enviado automaticamente
        });

        if (!response.ok) {
            // Refresh expirou — forçar login
            this.logout();
            return;
        }

        const data = await response.json();
        this.accessToken = data.accessToken;
        this.tokenExpiry = Date.now() + (14 * 60 * 1000);
        this.scheduleTokenRefresh();
    }

    getAuthHeaders(): HeadersInit {
        if (!this.accessToken || Date.now() > this.tokenExpiry!) {
            throw new Error('Sessão expirada');
        }
        return {
            'Authorization': `Bearer ${this.accessToken}`,
            'X-Requested-With': 'XMLHttpRequest' // CSRF mitigation adicional
        };
    }

    logout(): void {
        // Limpar memória
        this.accessToken = null;
        this.tokenExpiry = null;
        if (this.refreshTimer) clearTimeout(this.refreshTimer);

        // Invalidar cookie no servidor
        fetch('/api/auth/logout', {
            method: 'POST',
            credentials: 'include'
        });
    }

    // Lidar com refresh quando a aba fica ativa novamente
    handleVisibilityChange(): void {
        if (!document.hidden && this.accessToken) {
            // Verificar se o token ainda está válido
            if (Date.now() >= this.tokenExpiry!) {
                this.refreshAccessToken();
            }
        }
    }
}
```

### Problema: Perda do Token ao Recarregar a Página

```typescript
// Solução: Silent refresh — tentar renovar token ao carregar a app
// Se o refresh token (cookie) ainda for válido, obter novo access token silenciosamente

async initialize(): Promise<boolean> {
    try {
        // Tentar silent refresh — se falhar, usuário não estava logado
        await this.refreshAccessToken();
        return true; // estava logado
    } catch {
        return false; // não estava logado
    }
}
```

### Backend: Binding de Token ao Device

```java
// Incluir fingerprint no JWT para detectar session hijacking
public String generateAccessToken(User user, String deviceFingerprint) {
    return Jwts.builder()
        .subject(user.getId().toString())
        .claim("role", user.getRole().name())
        .claim("deviceFingerprint", deviceFingerprint) // binding ao device
        .issuedAt(new Date())
        .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
        .signWith(getSigningKey(), Jwts.SIG.HS256)
        .compact();
}
```

---

## Checklist Layer 6

- [ ] Rota admin calculada via HMAC do `ADMIN_ROUTE_SECRET` — nunca hardcoded
- [ ] Rotas que "parecem" admin redirecionam para honeypot silencioso
- [ ] IP allowlist verificada antes do Spring Security processar a request
- [ ] 5 camadas de verificação admin: rota secreta + IP + JWT ADMIN + 2FA + device binding
- [ ] Access token APENAS em variável de memória JS — zero localStorage/sessionStorage
- [ ] Refresh token em cookie `HttpOnly + Secure + SameSite=Strict`
- [ ] Renovação automática agendada 1 minuto antes do access token expirar
- [ ] Silent refresh ao inicializar a app (restaurar sessão sem relogin)
- [ ] Device fingerprint incluído no JWT (detectar session hijacking)
- [ ] `X-Requested-With` header em todas as requests autenticadas (CSRF mitigation adicional)
