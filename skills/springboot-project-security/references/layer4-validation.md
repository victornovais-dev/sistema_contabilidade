# Layer 4 — Validação, Sanitização, SQLi, XSS e IDOR

> **Mindset pentester:** Se você concatena qualquer coisa do usuário em SQL, você já perdeu.
> Se você renderiza qualquer coisa do usuário sem sanitizar, você já perdeu.
> Se você usa IDs sequenciais, qualquer script kiddie enumera sua base.

---

## IDOR Protection

### IDs Públicos como UUID v4

```java
@MappedSuperclass
public abstract class BaseEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false)
    private UUID id;
}
```

### Ownership Check — Sempre 404, Nunca 403

```java
@Component
public class OwnershipChecker {
    public <T extends OwnedResource> T verifyOwnership(
            UUID resourceId, UUID userId, JpaRepository<T, UUID> repo) {

        T resource = repo.findById(resourceId)
            .orElseThrow(ResourceNotFoundException::new);

        // 403 confirmaria que o recurso existe — sempre 404
        if (!resource.getOwnerId().equals(userId)) {
            throw new ResourceNotFoundException();
        }
        return resource;
    }
}
```

---

## SQL Injection — Defesa em 4 Níveis

> **Vetores reais:** Second-order SQLi, ORDER BY injection, LIKE injection, blind time-based.

### Nível 1 — ORM Exclusivo

```java
// NUNCA:
"SELECT * FROM users WHERE name LIKE '%" + input + "%'" // ← direto para o CTF do atacante

// SEMPRE — named param mesmo em LIKE:
@Query("SELECT u FROM User u WHERE u.name LIKE %:name%")
List<User> searchByName(@Param("name") String name);
```

### Nível 2 — ORDER BY via Whitelist (vetor ignorado por 95% dos devs)

```java
// ORDER BY não aceita parâmetros JDBC → devs ingênuos concatenam → SQLi clássico
@Component
public class SafeSortValidator {
    private static final Set<String> ALLOWED = Set.of(
        "created_at", "updated_at", "name", "title", "price", "amount"
    );

    public Sort validate(String field, String direction) {
        if (!ALLOWED.contains(field.toLowerCase()))
            throw new InvalidInputException("Campo de ordenação inválido");
        String dir = "DESC".equalsIgnoreCase(direction) ? "DESC" : "ASC";
        return Sort.by(Sort.Direction.fromString(dir), field);
    }
}
```

### Nível 3 — Detecção de Blind SQLi (time-based)

```java
@Aspect @Component
public class SqlInjectionDetectionAspect {

    private static final List<Pattern> SQL_PATTERNS = List.of(
        Pattern.compile("(?i)(union\\s+select|insert\\s+into|drop\\s+table|exec\\s*\\()"),
        Pattern.compile("(?i)(sleep\\s*\\(\\d+\\)|benchmark\\s*\\(|waitfor\\s+delay)"),
        Pattern.compile("(?i)(information_schema|sys\\.tables|pg_catalog|mysql\\.user)"),
        Pattern.compile("(?i)'\\s*(or|and)\\s+'?[\\d']+"),  // ' OR '1'='1
        Pattern.compile("--\\s*$|;\\s*--"),                  // comentários SQL
        Pattern.compile("(?i)0x[0-9a-f]{4,}")               // hex encoding evasion
    );

    @Before("@annotation(Audited)")
    public void detect(JoinPoint jp) {
        for (Object arg : jp.getArgs()) {
            if (arg instanceof String s) checkString(s);
        }
    }

    private void checkString(String s) {
        for (Pattern p : SQL_PATTERNS) {
            if (p.matcher(s).find()) {
                SecurityEventPublisher.publish(SecurityEvent.SQL_INJECTION_ATTEMPT, s);
                throw new InvalidInputException("Input inválido");
            }
        }
    }
}
```

### Nível 4 — Privilégios Mínimos no Banco

```yaml
# NUNCA root — usuário separado por função
spring:
  datasource:
    username: ${DB_APP_USER}    # SELECT, INSERT, UPDATE, DELETE apenas
    password: ${DB_APP_PASS}
  flyway:
    user: ${DB_MIGRATION_USER}  # DDL apenas para migrations — jamais para a app
    password: ${DB_MIGRATION_PASS}
```

---

## XSS — Defesa em 3 Camadas

> **Vetores reais:** Stored XSS em campos "inofensivos", DOM XSS via URL params, XSS em
> PDF/SVG gerados dinamicamente, mutation XSS em parsers permissivos, unicode bypass.

### Camada 1 — Sanitização com Decodificação Recursiva (evitar encoding evasion)

```java
@Component
public class InputSanitizer {

    private static final PolicyFactory TEXT_ONLY = new HtmlPolicyBuilder().toFactory();
    private static final PolicyFactory BASIC_FORMAT = new HtmlPolicyBuilder()
        .allowElements("b", "i", "em", "strong", "p", "br", "ul", "li")
        .requireRelNofollowOnLinks()
        .toFactory();

    public String sanitizeText(String input) {
        if (input == null) return null;
        // Normalizar unicode antes — evita bypass via lookalike chars (ℯval, ᴊavascript:)
        String normalized = Normalizer.normalize(input.trim(), Normalizer.Form.NFKC);
        return TEXT_ONLY.sanitize(normalized);
    }

    public void assertNoXssPayload(String input, String field) {
        if (input == null) return;
        if (input.length() > 50_000)
            throw new InvalidInputException(field + ": input muito longo");

        // Decodificar múltiplas vezes — atacantes usam double/triple URL encoding
        String decoded = input;
        for (int i = 0; i < 3; i++) {
            try {
                String prev = decoded;
                decoded = URLDecoder.decode(decoded, StandardCharsets.UTF_8);
                if (decoded.equals(prev)) break;
            } catch (Exception ignored) { break; }
        }

        List<String> patterns = List.of(
            "<script", "javascript:", "vbscript:", "data:text/html",
            "onerror=", "onload=", "onclick=", "onmouseover=", "onfocus=",
            "expression(", "eval(", "document.cookie", "document.write",
            "innerHTML", "outerHTML", "insertAdjacentHTML",
            "&#x", "&#0", "%3cscript", "\\u003cscript", "\\x3cscript"
        );

        String lower = decoded.toLowerCase();
        for (String p : patterns) {
            if (lower.contains(p)) {
                SecurityEventPublisher.publish(SecurityEvent.XSS_ATTEMPT,
                    field + ": " + input.substring(0, Math.min(200, input.length())));
                throw new InvalidInputException("Input inválido: " + field);
            }
        }
    }
}
```

### Camada 2 — CSP com Nonce por Request (sem 'unsafe-inline')

```java
@Component @Order(1)
public class CspNonceFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest req,
                                    HttpServletResponse res,
                                    FilterChain chain) throws ServletException, IOException {
        byte[] bytes = new byte[16];
        new SecureRandom().nextBytes(bytes);
        String nonce = Base64.getEncoder().encodeToString(bytes);
        req.setAttribute("cspNonce", nonce);

        res.setHeader("Content-Security-Policy",
            "default-src 'self'; " +
            "script-src 'self' 'nonce-" + nonce + "'; " +
            "style-src 'self' 'unsafe-inline'; " +
            "img-src 'self' data: https:; " +
            "connect-src 'self'; " +
            "frame-ancestors 'none'; " +
            "base-uri 'self'; " +                 // evita base tag injection
            "form-action 'self'; " +              // evita form hijacking
            "object-src 'none'; " +               // sem Flash/plugins
            "upgrade-insecure-requests");

        res.setHeader("X-Content-Type-Options", "nosniff");
        res.setHeader("X-Frame-Options", "DENY");
        res.setHeader("X-XSS-Protection", "0");   // desabilitar auditor antigo (causa bypasses)
        res.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        res.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=()");

        chain.doFilter(req, res);
    }
}
```

### Camada 3 — Sanitização de Output em Templates Thymeleaf

```html
<!-- SEGURO — auto-escape -->
<span th:text="${user.bio}"></span>

<!-- PERIGOSO — nunca sem sanitização prévia -->
<!-- <span th:utext="${user.bio}"></span> -->

<!-- Se rich text for necessário — sanitizar explicitamente -->
<span th:utext="${@sanitizer.sanitizeRichText(user.bio)}"></span>
```

---

## Mass Assignment & Parameter Pollution

```java
// Jackson: rejeitar campos desconhecidos e chaves duplicadas
@Bean
public Jackson2ObjectMapperBuilderCustomizer jacksonConfig() {
    return builder -> builder
        .featuresToEnable(
            JsonParser.Feature.STRICT_DUPLICATE_DETECTION,
            DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
        .featuresToDisable(MapperFeature.DEFAULT_VIEW_INCLUSION);
}
```

---

## Checklist Layer 4

- [ ] UUIDs v4 — zero IDs sequenciais
- [ ] Ownership check — sempre 404, nunca 403
- [ ] ORDER BY via whitelist — nunca concatenado
- [ ] Detecção de blind SQLi (sleep, benchmark, waitfor, hex encoding)
- [ ] Usuário DB com SELECT/INSERT/UPDATE/DELETE apenas — sem DDL
- [ ] Sanitização com normalização Unicode NFKC
- [ ] Decodificação multi-camada antes de checar XSS
- [ ] CSP com nonce por request — sem unsafe-inline
- [ ] `FAIL_ON_UNKNOWN_PROPERTIES` + `STRICT_DUPLICATE_DETECTION` no Jackson
- [ ] `@Valid` em 100% dos controllers
