# Layer 9 — Proteção CSRF

> **Mindset pentester:** CSRF é simples: induzo sua vítima a acessar minha página,
> que faz um request para sua aplicação usando as credenciais dela (cookies).
> A defesa moderna não é só o CSRF token — é o double-submit, SameSite, e
> verificação de Origin. Sem todos os três, há bypass.

---

## Por Que Só SameSite=Strict Não Basta?

```
# SameSite=Strict protege contra requests cross-site iniciados pelo browser
# MAS: subdomínios comprometidos, CORS misconfiguration, e alguns browsers
# ignoram ou têm bugs em SameSite ainda em 2025.
# Defense in depth: SameSite + CSRF Token + Origin Check
```

---

## Implementação: Double-Submit Cookie Pattern

```java
// filter/CsrfProtectionFilter.java
@Component @Order(3)
public class CsrfProtectionFilter extends OncePerRequestFilter {

    private static final Set<String> SAFE_METHODS = Set.of("GET", "HEAD", "OPTIONS", "TRACE");
    private static final String CSRF_COOKIE_NAME = "XSRF-TOKEN";
    private static final String CSRF_HEADER_NAME = "X-XSRF-TOKEN";

    @Value("${app.allowed-origins}")
    private String allowedOrigins;

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {

        // Métodos seguros não precisam de verificação CSRF
        if (SAFE_METHODS.contains(req.getMethod())) {
            // Mas garantir que o cookie CSRF existe para o frontend usar
            ensureCsrfCookieExists(req, res);
            chain.doFilter(req, res);
            return;
        }

        // 1. Verificar Origin/Referer — primeira linha de defesa
        if (!isOriginAllowed(req)) {
            SecurityEventPublisher.publish(SecurityEvent.CSRF_ORIGIN_MISMATCH,
                "Origin: " + req.getHeader("Origin") + " | Referer: " + req.getHeader("Referer"));
            sendCsrfError(res, "Origin não permitido");
            return;
        }

        // 2. Double-submit cookie verification
        String cookieToken = getCsrfCookie(req);
        String headerToken = req.getHeader(CSRF_HEADER_NAME);

        if (cookieToken == null || headerToken == null) {
            sendCsrfError(res, "CSRF token ausente");
            return;
        }

        // Timing-safe comparison — evitar timing attack no próprio CSRF
        if (!MessageDigest.isEqual(
                cookieToken.getBytes(StandardCharsets.UTF_8),
                headerToken.getBytes(StandardCharsets.UTF_8))) {
            SecurityEventPublisher.publish(SecurityEvent.CSRF_TOKEN_MISMATCH, req.getRequestURI());
            sendCsrfError(res, "CSRF token inválido");
            return;
        }

        chain.doFilter(req, res);
    }

    private boolean isOriginAllowed(HttpServletRequest req) {
        Set<String> allowed = Set.of(allowedOrigins.split(","));

        // Verificar Origin header primeiro (presente em maioria dos browsers modernos)
        String origin = req.getHeader("Origin");
        if (origin != null && !origin.isBlank()) {
            return allowed.stream().anyMatch(a -> a.trim().equals(origin.trim()));
        }

        // Fallback para Referer se Origin não estiver presente
        String referer = req.getHeader("Referer");
        if (referer != null && !referer.isBlank()) {
            try {
                URL refererUrl = new URL(referer);
                String refererOrigin = refererUrl.getProtocol() + "://" + refererUrl.getHost();
                int port = refererUrl.getPort();
                if (port > 0) refererOrigin += ":" + port;
                final String finalOrigin = refererOrigin;
                return allowed.stream().anyMatch(a -> a.trim().equals(finalOrigin));
            } catch (MalformedURLException e) {
                return false;
            }
        }

        // Sem Origin nem Referer — bloquear (exceto requests de mesma origem real)
        return false;
    }

    private void ensureCsrfCookieExists(HttpServletRequest req, HttpServletResponse res) {
        if (getCsrfCookie(req) == null) {
            // Gerar novo CSRF token
            byte[] tokenBytes = new byte[32];
            new SecureRandom().nextBytes(tokenBytes);
            String token = Base64.getUrlEncoder().withoutPadding().encodeToString(tokenBytes);

            // Cookie CSRF: NÃO HttpOnly — o frontend JS precisa lê-lo para enviar no header
            ResponseCookie cookie = ResponseCookie.from(CSRF_COOKIE_NAME, token)
                .secure(true)
                .sameSite("Strict")
                .path("/")
                .maxAge(Duration.ofHours(4))
                .build();

            res.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        }
    }

    private String getCsrfCookie(HttpServletRequest req) {
        if (req.getCookies() == null) return null;
        return Arrays.stream(req.getCookies())
            .filter(c -> CSRF_COOKIE_NAME.equals(c.getName()))
            .map(Cookie::getValue)
            .findFirst()
            .orElse(null);
    }

    private void sendCsrfError(HttpServletResponse res, String message) throws IOException {
        res.setStatus(HttpServletResponse.SC_FORBIDDEN);
        res.setContentType("application/json");
        res.getWriter().write("{\"error\":\"Requisição inválida\"}"); // mensagem genérica
    }
}
```

---

## Configuração Spring Security para CSRF Custom

```java
// config/SecurityConfig.java
http.csrf(csrf -> csrf
    .disable() // Desabilitar CSRF do Spring — usar nosso filtro customizado
    // O filtro customizado é mais flexível e permite double-submit pattern
);
```

---

## Frontend: Ler Cookie e Enviar no Header

```typescript
// utils/csrf.ts
function getCsrfToken(): string | null {
    const match = document.cookie.match(/XSRF-TOKEN=([^;]+)/);
    return match ? decodeURIComponent(match[1]) : null;
}

// Interceptor para Angular HttpClient
@Injectable()
export class CsrfInterceptor implements HttpInterceptor {
    intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
        const safeMethods = ['GET', 'HEAD', 'OPTIONS'];

        if (safeMethods.includes(req.method)) {
            return next.handle(req);
        }

        const token = getCsrfToken();
        if (!token) {
            console.error('CSRF token não encontrado');
        }

        const modified = req.clone({
            headers: req.headers.set('X-XSRF-TOKEN', token || '')
        });

        return next.handle(modified);
    }
}

// Interceptor para fetch nativo
async function safeFetch(url: string, options: RequestInit = {}): Promise<Response> {
    const method = options.method?.toUpperCase() || 'GET';
    const safeMethods = ['GET', 'HEAD', 'OPTIONS'];

    if (!safeMethods.includes(method)) {
        options.headers = {
            ...options.headers,
            'X-XSRF-TOKEN': getCsrfToken() || '',
            'X-Requested-With': 'XMLHttpRequest'
        };
    }

    return fetch(url, { ...options, credentials: 'include' });
}
```

---

## Proteção Adicional: Custom Request Header Requirement

```java
// Qualquer request de browser cross-site não pode setar headers customizados (CORS bloqueia)
// Verificar presença de X-Requested-With como camada adicional

@Component
public class CustomHeaderCsrfVerifier {

    public boolean hasCustomHeader(HttpServletRequest req) {
        return "XMLHttpRequest".equals(req.getHeader("X-Requested-With"));
    }
}
```

---

## CORS Configuração Segura

```java
// config/CorsConfig.java
@Configuration
public class CorsConfig {

    @Value("${app.allowed-origins}")
    private String allowedOrigins;

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();

        // NUNCA "*" em produção
        config.setAllowedOrigins(Arrays.asList(allowedOrigins.split(",")));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of(
            "Content-Type", "Authorization", "X-XSRF-TOKEN", "X-Requested-With"
        ));
        config.setAllowCredentials(true); // necessário para cookies
        config.setMaxAge(3600L); // cache de preflight: 1 hora

        // NUNCA expor headers sensíveis
        config.setExposedHeaders(List.of("X-Request-Id"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
```

---

## Checklist Layer 9

- [ ] Double-submit cookie pattern implementado
- [ ] Cookie CSRF: `Secure + SameSite=Strict` — mas NÃO HttpOnly (JS precisa ler)
- [ ] Verificação de Origin header em todas as mutations (POST/PUT/PATCH/DELETE)
- [ ] Fallback para Referer se Origin ausente
- [ ] Request sem Origin nem Referer: bloquear
- [ ] CSRF token comparado com `MessageDigest.isEqual()` (timing-safe)
- [ ] `X-Requested-With: XMLHttpRequest` como camada adicional
- [ ] CORS: sem wildcard, `allowCredentials=true`, origens explícitas
- [ ] CORS: headers permitidos listados explicitamente
- [ ] Interceptor no frontend enviando `X-XSRF-TOKEN` em todas as mutations
