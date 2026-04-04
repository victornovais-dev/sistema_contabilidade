# Layer 1 — Autenticação Blindada

## Senhas com Argon2id

**Nunca usar BCrypt** — Argon2id é a escolha correta para novos projetos.

```java
// config/PasswordEncoderConfig.java
@Configuration
public class PasswordEncoderConfig {

    @Value("${security.argon2.memory-cost:65536}")
    private int memoryCost; // 64MB

    @Value("${security.argon2.time-cost:3}")
    private int timeCost;

    @Value("${security.argon2.parallelism:2}")
    private int parallelism;

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(
            16,          // saltLength
            32,          // hashLength
            parallelism,
            memoryCost,
            timeCost
        );
    }
}
```

**.env / application.yml:**
```yaml
security:
  argon2:
    memory-cost: 65536
    time-cost: 3
    parallelism: 2
```

---

## Timing-Safe Comparison

Toda verificação de token/hash deve ser timing-safe para evitar timing attacks:

```java
// utils/SecurityUtils.java
public class SecurityUtils {

    /**
     * Timing-safe string comparison.
     * Evita timing attacks em verificações de token.
     */
    public static boolean safeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] aBytes = a.getBytes(StandardCharsets.UTF_8);
        byte[] bBytes = b.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(aBytes, bBytes);
    }
}
```

---

## Resposta de Erro Genérica

**Nunca revelar** se foi "email não encontrado" ou "senha errada":

```java
// service/AuthService.java
public AuthResponse login(LoginRequest request) {
    // Busca o usuário — não revela se existe ou não
    Optional<User> userOpt = userRepository.findByEmail(request.getEmail());

    // SEMPRE executar o hash, mesmo se usuário não existe (evitar timing attack)
    String hashParaComparar = userOpt
        .map(User::getPasswordHash)
        .orElse("$argon2id$v=19$m=65536,t=3,p=2$dummy$dummyhashtopreventtiming");

    boolean senhaCorreta = passwordEncoder.matches(request.getPassword(), hashParaComparar);

    if (userOpt.isEmpty() || !senhaCorreta) {
        // SEMPRE a mesma mensagem genérica
        throw new InvalidCredentialsException("Credenciais inválidas");
    }

    return gerarTokens(userOpt.get());
}
```

```java
// exception/GlobalExceptionHandler.java
@ExceptionHandler(InvalidCredentialsException.class)
public ResponseEntity<Map<String, String>> handleInvalidCredentials(InvalidCredentialsException ex) {
    return ResponseEntity
        .status(HttpStatus.UNAUTHORIZED)
        .body(Map.of("error", "Credenciais inválidas")); // mensagem genérica sempre
}
```

---

## JWT — Configuração Segura

### Algoritmo fixado em HS256

```java
// security/JwtService.java
@Service
public class JwtService {

    @Value("${jwt.secret}")
    private String secret;

    // Access token: 15 minutos
    private static final long ACCESS_TOKEN_EXPIRY = 15 * 60 * 1000L;

    // Refresh token: 7 dias
    private static final long REFRESH_TOKEN_EXPIRY = 7 * 24 * 60 * 60 * 1000L;

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(Decoders.BASE64.decode(secret));
    }

    public String generateAccessToken(User user) {
        return Jwts.builder()
            .subject(user.getId().toString())
            .claim("email", user.getEmail())
            .claim("role", user.getRole().name())
            .issuedAt(new Date())
            .expiration(new Date(System.currentTimeMillis() + ACCESS_TOKEN_EXPIRY))
            .signWith(getSigningKey(), Jwts.SIG.HS256) // algoritmo FIXADO
            .compact();
    }

    public Claims validateAndExtractClaims(String token) {
        try {
            return Jwts.parser()
                .verifyWith(getSigningKey())
                // REJEITAR qualquer alg diferente de HS256
                .require("alg", "HS256")
                .build()
                .parseSignedClaims(token)
                .getPayload();
        } catch (JwtException e) {
            throw new InvalidTokenException("Token inválido ou expirado");
        }
    }

    // Gerar refresh token opaco (não JWT) — armazenar hash no banco
    public String generateRefreshToken() {
        byte[] randomBytes = new byte[64];
        new SecureRandom().nextBytes(randomBytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
    }

    public String hashRefreshToken(String rawToken) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return Base64.getEncoder().encodeToString(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 não disponível", e);
        }
    }
}
```

---

## Refresh Token — Armazenamento e Revogação

```java
// entity/RefreshToken.java
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken {
    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String tokenHash; // SHA-256 do token real

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked = false;

    private String deviceFingerprint; // para revogação por dispositivo
}
```

```java
// service/AuthService.java — método de login completo
public AuthResponse login(LoginRequest request, HttpServletResponse response) {
    User user = autenticarOuLancarErro(request);

    // Session fixation protection: sempre gerar par NOVO de tokens
    invalidarTokensAntigosDoUsuario(user); // revogar sessões anteriores se necessário

    String rawRefreshToken = jwtService.generateRefreshToken();
    String refreshTokenHash = jwtService.hashRefreshToken(rawRefreshToken);

    RefreshToken refreshToken = new RefreshToken();
    refreshToken.setUser(user);
    refreshToken.setTokenHash(refreshTokenHash);
    refreshToken.setExpiresAt(Instant.now().plus(7, ChronoUnit.DAYS));
    refreshTokenRepository.save(refreshToken);

    // Access token vai no body
    String accessToken = jwtService.generateAccessToken(user);

    // Refresh token vai APENAS no cookie HttpOnly
    ResponseCookie cookie = ResponseCookie.from("refresh_token", rawRefreshToken)
        .httpOnly(true)
        .secure(true)                    // HTTPS only
        .sameSite("Strict")             // SameSite=Strict
        .path("/api/auth/refresh")       // path restrito
        .maxAge(Duration.ofDays(7))
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

    return new AuthResponse(accessToken); // NUNCA retornar refresh token no body
}
```

---

## Logout — Invalidar Refresh Token no Banco

```java
public void logout(String rawRefreshToken, HttpServletResponse response) {
    String tokenHash = jwtService.hashRefreshToken(rawRefreshToken);

    // Invalidar no banco (não é stateless — é intencional para suportar revogação)
    refreshTokenRepository.findByTokenHash(tokenHash)
        .ifPresent(token -> {
            token.setRevoked(true);
            refreshTokenRepository.save(token);
        });

    // Limpar o cookie
    ResponseCookie cookie = ResponseCookie.from("refresh_token", "")
        .httpOnly(true)
        .secure(true)
        .sameSite("Strict")
        .path("/api/auth/refresh")
        .maxAge(0) // expira imediatamente
        .build();

    response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
}
```

---

## Spring Security Config Base

```java
// config/SecurityConfig.java
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Autowired
    private JwtAuthenticationFilter jwtFilter;

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable()) // API stateless — CSRF via SameSite=Strict no cookie
            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers
                .frameOptions(HeadersConfigurer.FrameOptionsConfig::deny)
                .contentTypeOptions(Customizer.withDefaults())
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/auth/login", "/api/auth/register",
                                 "/api/auth/refresh", "/api/auth/recover").permitAll()
                .anyRequest().authenticated())
            .addFilterBefore(jwtFilter, UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setContentType("application/json");
                    res.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
                    res.getWriter().write("{\"error\":\"Não autorizado\"}");
                }));

        return http.build();
    }
}
```

---

## Checklist Layer 1

- [ ] Argon2id configurado com parâmetros via .env
- [ ] Hash dummy executado mesmo para usuários inexistentes (timing attack)
- [ ] Erro de login sempre retorna "Credenciais inválidas" (genérico)
- [ ] JWT com algoritmo HS256 fixado — `none`, `RS256` etc. rejeitados
- [ ] Access token: 15 min, apenas no body/memory do frontend
- [ ] Refresh token: 7 dias, hash no banco, nunca no body da resposta
- [ ] Refresh token no cookie `HttpOnly + Secure + SameSite=Strict`
- [ ] Logout invalida o refresh token no banco
- [ ] Novos tokens gerados a cada login (session fixation protection)
- [ ] Respostas de erro sem stack trace
