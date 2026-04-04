# Layer 10 — Auditoria de Login e Eventos de Segurança

> **Mindset pentester:** Logs ruins são cúmplices do atacante. Se você não pode reconstruir
> o que aconteceu depois de um breach, você não tem segurança — tem falsa segurança.
> Logs também são o único jeito de detectar ataques lentos e distribuídos que burlam
> rate limiting por usarem IPs diferentes.

---

## Modelo de Evento de Segurança

```java
// entity/SecurityAuditLog.java
@Entity
@Table(name = "security_audit_logs", indexes = {
    @Index(name = "idx_audit_user", columnList = "user_id"),
    @Index(name = "idx_audit_event", columnList = "event_type"),
    @Index(name = "idx_audit_ip", columnList = "ip_address"),
    @Index(name = "idx_audit_time", columnList = "occurred_at")
})
public class SecurityAuditLog {

    @Id @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SecurityEventType eventType;

    @Column(name = "user_id")
    private UUID userId; // nullable — eventos pré-autenticação

    @Column(nullable = false)
    private String ipAddress;

    @Column(columnDefinition = "TEXT")
    private String userAgent;

    private String deviceFingerprint;

    // Detalhes adicionais do evento (JSON)
    @Column(columnDefinition = "TEXT")
    private String metadata;

    // Resultado: SUCCESS, FAILURE, BLOCKED
    @Enumerated(EnumType.STRING)
    private AuditResult result;

    @Column(nullable = false)
    private Instant occurredAt;

    // Geolocalização aproximada (não armazenar localização exata)
    private String countryCode;
    private String region;

    // Para correlação de incidentes
    private String sessionId;
    private String requestId;
}
```

```java
// enum/SecurityEventType.java
public enum SecurityEventType {
    // Autenticação
    LOGIN_SUCCESS,
    LOGIN_FAILURE,
    LOGIN_LOCKED_OUT,
    LOGIN_BOT_DETECTED,
    LOGOUT,
    TOKEN_REFRESH,
    TOKEN_REFRESH_FAILED,
    TOKEN_REVOKED,

    // 2FA
    MFA_SETUP,
    MFA_VERIFIED,
    MFA_FAILED,
    MFA_BYPASSED_ATTEMPT,

    // Autorização
    UNAUTHORIZED_ACCESS,
    PRIVILEGE_ESCALATION_ATTEMPT,
    IDOR_ATTEMPT,
    ADMIN_ACCESS,
    ADMIN_2FA_FAILED,

    // Ataques
    SQL_INJECTION_ATTEMPT,
    XSS_ATTEMPT,
    SSRF_ATTEMPT,
    PATH_TRAVERSAL_ATTEMPT,
    CSRF_TOKEN_MISMATCH,
    CSRF_ORIGIN_MISMATCH,
    SESSION_HIJACK_ATTEMPT,
    FILE_MIME_MISMATCH,
    MALICIOUS_PDF,
    BOT_DETECTED,
    CRAWLER_DETECTED,

    // Honeypots
    HONEYPOT_HIT,
    ADMIN_ROUTE_PROBE,

    // Dados
    SENSITIVE_DATA_ACCESS,
    BULK_DATA_ACCESS,
    DATA_EXPORT,
    PASSWORD_CHANGED,
    EMAIL_CHANGED,
    ACCOUNT_DELETED,

    // Sistema
    RATE_LIMIT_EXCEEDED,
    UNUSUAL_LOCATION,
    CONCURRENT_SESSION_DETECTED,
}
```

---

## SecurityEventPublisher — Publicação Assíncrona

```java
// event/SecurityEventPublisher.java
@Component
public class SecurityEventPublisher {

    private static ApplicationEventPublisher publisher;

    @Autowired
    public void setPublisher(ApplicationEventPublisher pub) {
        SecurityEventPublisher.publisher = pub;
    }

    public static void publish(SecurityEventType type, String detail) {
        if (publisher != null) {
            publisher.publishEvent(new SecurityEvent(type, detail));
        }
    }
}

// event/SecurityEventListener.java
@Component
public class SecurityEventListener {

    @Autowired private SecurityAuditLogRepository auditRepo;
    @Autowired private SecurityAlertService alertService;
    @Autowired private HttpServletRequest request; // request scope bean

    @Async // não bloquear o request principal
    @EventListener
    public void handleSecurityEvent(SecurityEvent event) {
        SecurityAuditLog log = new SecurityAuditLog();
        log.setEventType(event.getType());
        log.setIpAddress(getClientIp());
        log.setUserAgent(request.getHeader("User-Agent"));
        log.setOccurredAt(Instant.now());
        log.setMetadata(event.getDetail());
        log.setRequestId((String) request.getAttribute("X-Request-Id"));

        auditRepo.save(log);

        // Verificar se precisa alertar
        if (isCriticalEvent(event.getType())) {
            alertService.sendAlert(event);
        }

        // Verificar padrões de ataque
        detectAttackPatterns(log);
    }

    private void detectAttackPatterns(SecurityAuditLog log) {
        // Detectar scanning: mesmo IP tentando muitos endpoints diferentes
        long distinctEndpoints = auditRepo.countDistinctEndpointsByIpInLast10Minutes(log.getIpAddress());
        if (distinctEndpoints > 20) {
            alertService.sendAlert("Possível scanning: " + log.getIpAddress());
        }

        // Detectar credential stuffing: muitos IPs diferentes falhando com mesmos emails
        long failedLogins = auditRepo.countFailedLoginsByEmailInLastHour(log.getMetadata());
        if (failedLogins > 50) {
            alertService.sendAlert("Possível credential stuffing para email: " + log.getMetadata());
        }
    }

    private boolean isCriticalEvent(SecurityEventType type) {
        return Set.of(
            SecurityEventType.SQL_INJECTION_ATTEMPT,
            SecurityEventType.SSRF_ATTEMPT,
            SecurityEventType.ADMIN_ACCESS,
            SecurityEventType.PRIVILEGE_ESCALATION_ATTEMPT,
            SecurityEventType.SESSION_HIJACK_ATTEMPT,
            SecurityEventType.MALICIOUS_PDF,
            SecurityEventType.ADMIN_2FA_FAILED
        ).contains(type);
    }
}
```

---

## Auditoria de Login Completa

```java
// service/AuthService.java — login com auditoria completa
public AuthResponse login(LoginRequest req, HttpServletRequest httpReq) {

    String ip = fingerprintService.getClientIp(httpReq);
    String fingerprint = fingerprintService.generateFingerprint(httpReq);

    // Verificar lockout ANTES de qualquer processamento
    if (loginAttemptService.isLockedOut(ip)) {
        SecurityEventPublisher.publish(SecurityEventType.LOGIN_LOCKED_OUT, ip);
        // Silencioso — mesmo response que credenciais inválidas
        simulateDummyHash(); // manter timing consistente
        throw new InvalidCredentialsException("Credenciais inválidas");
    }

    Optional<User> userOpt = userRepository.findByEmail(req.getEmail());

    // Dummy hash se usuário não existe (timing attack prevention)
    String hashToCheck = userOpt.map(User::getPasswordHash)
        .orElse("$argon2id$v=19$m=65536,t=3,p=2$dummysalt$dummyhash");

    boolean valid = passwordEncoder.matches(req.getPassword(), hashToCheck);

    if (userOpt.isEmpty() || !valid) {
        loginAttemptService.registerFailedAttempt(ip);

        // Logar falha com contexto completo
        auditLogin(null, ip, fingerprint, AuditResult.FAILURE,
            "email_tentado=" + req.getEmail().substring(0, Math.min(3, req.getEmail().length())) + "***");

        throw new InvalidCredentialsException("Credenciais inválidas");
    }

    User user = userOpt.get();

    // Detectar login de localização incomum
    detectUnusualLocation(user, ip, fingerprint);

    // Detectar sessão concorrente suspeita
    detectConcurrentSessions(user, fingerprint);

    loginAttemptService.resetAttempts(ip);

    // Logar sucesso
    auditLogin(user.getId(), ip, fingerprint, AuditResult.SUCCESS, null);

    return gerarTokens(user, fingerprint);
}

private void detectUnusualLocation(User user, String ip, String fingerprint) {
    // Verificar se este fingerprint/IP já foi visto para este usuário
    boolean knownDevice = auditRepo.hasSuccessfulLoginFromFingerprint(user.getId(), fingerprint);

    if (!knownDevice) {
        SecurityEventPublisher.publish(SecurityEventType.UNUSUAL_LOCATION,
            "user=" + user.getId() + " | ip=" + ip);

        // Opcional: enviar email de alerta para o usuário
        notificationService.sendLoginFromNewDeviceAlert(user, ip);
    }
}

private void auditLogin(UUID userId, String ip, String fingerprint,
                         AuditResult result, String metadata) {
    SecurityAuditLog log = new SecurityAuditLog();
    log.setEventType(result == AuditResult.SUCCESS
        ? SecurityEventType.LOGIN_SUCCESS
        : SecurityEventType.LOGIN_FAILURE);
    log.setUserId(userId);
    log.setIpAddress(ip);
    log.setDeviceFingerprint(fingerprint);
    log.setResult(result);
    log.setMetadata(metadata);
    log.setOccurredAt(Instant.now());
    auditRepo.save(log);
}
```

---

## Proteção dos Logs Contra Manipulação

```java
// Os logs de segurança NUNCA devem poder ser deletados pela aplicação
// Usar usuário DB separado para audit logs com apenas INSERT

// application.yml
spring:
  datasource:
    security-audit:
      url: ${DB_AUDIT_URL}
      username: ${DB_AUDIT_USER}    # apenas INSERT — sem UPDATE, DELETE
      password: ${DB_AUDIT_PASS}

// Opcional: WORM storage — logs gravados em S3 com Object Lock
// Protege contra atacante que compromete o banco e tenta apagar rastros
```

---

## Dashboard de Alertas em Tempo Real

```java
// service/SecurityAlertService.java
@Service
public class SecurityAlertService {

    @Autowired private JavaMailSender mailSender;
    @Autowired private SlackNotificationClient slack; // opcional

    @Value("${security.alert.email}")
    private String alertEmail;

    // Throttle de alertas — evitar spam em ataques de volume
    private final Map<String, Instant> lastAlertTime = new ConcurrentHashMap<>();

    public void sendAlert(SecurityEvent event) {
        String key = event.getType().name();
        Instant lastAlert = lastAlertTime.get(key);

        // Máximo 1 alerta por tipo a cada 5 minutos
        if (lastAlert != null && lastAlert.isAfter(Instant.now().minus(5, ChronoUnit.MINUTES))) {
            return;
        }

        lastAlertTime.put(key, Instant.now());

        // Email
        SimpleMailMessage mail = new SimpleMailMessage();
        mail.setTo(alertEmail);
        mail.setSubject("[SECURITY ALERT] " + event.getType().name());
        mail.setText("Evento: " + event.getType() + "\nDetalhes: " + event.getDetail()
            + "\nTimestamp: " + Instant.now());
        mailSender.send(mail);

        // Slack (se configurado)
        slack.sendMessage("#security-alerts",
            "🚨 *" + event.getType().name() + "*\n" + event.getDetail());
    }
}
```

---

## Retenção e Anonimização de Logs

```java
// scheduler/LogRetentionScheduler.java
@Component
public class LogRetentionScheduler {

    @Scheduled(cron = "0 0 3 * * ?") // 3h da manhã diariamente
    public void anonymizeOldLogs() {
        // Após 90 dias: anonimizar IP e fingerprint
        Instant cutoff = Instant.now().minus(90, ChronoUnit.DAYS);
        auditRepo.anonymizeLogsOlderThan(cutoff);

        // Após 2 anos: deletar logs não críticos (compliance LGPD/GDPR)
        Instant deleteCutoff = Instant.now().minus(730, ChronoUnit.DAYS);
        auditRepo.deleteNonCriticalLogsOlderThan(deleteCutoff);
    }
}
```

---

## Checklist Layer 10

- [ ] Todos os eventos de segurança têm enum tipado (`SecurityEventType`)
- [ ] Publicação de eventos assíncrona — não bloquear request principal
- [ ] Login bem-sucedido auditado: userId, IP, fingerprint, timestamp
- [ ] Login falho auditado: IP, fingerprint, email parcial mascarado
- [ ] Detecção de login de device/localização nova → alerta ao usuário
- [ ] Detecção de sessões concorrentes suspeitas
- [ ] Eventos críticos disparam alerta imediato (email/Slack)
- [ ] Throttle de alertas (máx 1 por tipo a cada 5 min) — evitar spam
- [ ] Usuário DB de audit logs com apenas INSERT (sem UPDATE/DELETE)
- [ ] Detecção de padrões: scanning, credential stuffing
- [ ] Retenção: anonimização após 90 dias, deleção após 2 anos (LGPD)
- [ ] Logs nunca contêm: senhas, tokens completos, dados sensíveis
- [ ] Request ID propagado para correlação entre logs de aplicação e segurança
