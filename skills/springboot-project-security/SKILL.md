---
name: springboot-security
description: >
  Implementar segurança production-grade em camadas para APIs Java Spring Boot.
  Use esta skill SEMPRE que o usuário perguntar sobre: autenticação, JWT, Spring Security,
  rate limiting, brute force, IDOR, validação de input, XSS, SQL injection, honeypots
  (backend ou frontend), CSRF, SSRF, path traversal, upload de arquivos, rota admin,
  JWT em memória, auditoria de segurança, renderização segura (PDF/Markdown/imagens),
  hardening de MySQL, deserialization attacks, race conditions, HTTP request smuggling,
  cache poisoning, ou qualquer tópico de segurança em Spring Boot.
  Também trigger quando o usuário compartilha um controller, service ou config Spring
  e pede review de segurança, ou pergunta "isso é seguro?", "como protejo X?".
  Esta skill cobre 13 camadas defensivas modeladas por um pentester sênior com
  abordagem attacker-first: primeiro o vetor de ataque, depois a defesa.
---

# Spring Boot Security — 13 Camadas de Defesa (Attacker-First)

> **Filosofia:** Antes de implementar qualquer defesa, entenda o ataque.
> Cada camada desta skill começa com o vetor real que ela defende.
> Segurança sem contexto de ataque é teatro.

## Índice de Camadas

| # | Camada | Ameaças Principais | Arquivo |
|---|--------|--------------------|---------|
| 1 | Autenticação Blindada | Password cracking, JWT bypass, session fixation | `layer1-auth.md` |
| 2 | Rate Limiting Anti-Brute Force | Credential stuffing, brute force, DDoS | `layer2-ratelimit.md` |
| 3 | Honeypots Backend | Scanners, bots, reconhecimento de infraestrutura | `layer3-honeypot.md` |
| 4 | Validação, SQLi, XSS, IDOR | SQL injection, XSS stored/reflected/DOM, IDOR | `layer4-validation.md` |
| 5 | Upload de Arquivos | MIME spoofing, polyglot files, ZIP bomb, path traversal | `layer5-file-upload.md` |
| 6 | Admin Invisível + JWT em Memória | Admin route enumeration, XSS token theft, session hijack | `layer6-admin-jwt-memory.md` |
| 7 | Honeypots Frontend | Bots, form automation, headless browsers, crawlers | `layer7-frontend-honeypots.md` |
| 8 | SSRF + Path Traversal | AWS IMDS, internal service access, LFI | `layer8-ssrf-path-traversal.md` |
| 9 | CSRF | Cross-site request forgery, CORS misconfiguration | `layer9-csrf.md` |
| 10 | Auditoria e Logs | Ataques não detectados, breach reconstruction, compliance | `layer10-audit-logging.md` |
| 11 | Segurança de Renderização | PDF SSRF, Markdown XSS, SSTI, ImageMagick RCE | `layer11-rendering-security.md` |
| 12 | Hardening MySQL | SQLi via DB, privilege escalation, data exfiltration | `layer12-mysql-hardening.md` |
| 13 | Técnicas Avançadas de Pentester | HTTP smuggling, deserialization, race conditions, etc. | `layer13-advanced-pentester.md` |

---

## Guia de Decisão Rápida

```
Pergunta sobre senha/login/token?           → layer1 + layer6
Pergunta sobre bots/brute force?            → layer2 + layer7
Pergunta sobre input do usuário?            → layer4
Pergunta sobre upload de arquivo?           → layer5
Pergunta sobre admin/roles?                 → layer6
Pergunta sobre CSRF/CORS?                   → layer9
Pergunta sobre URL externa/fetch/webhook?   → layer8
Pergunta sobre logs/compliance?             → layer10
Pergunta sobre PDF/Markdown/imagem?         → layer11
Pergunta sobre banco de dados?              → layer4 (SQLi) + layer12
Review de segurança geral?                  → ler TODAS as camadas
```

---

## Regras Universais (Aplicar em 100% do Código)

1. **Segredos nunca em código** — sempre via `@Value("${...}")` de variáveis de ambiente
2. **Todos os IDs públicos são UUID v4** — zero inteiros sequenciais
3. **Comparações timing-safe** — `MessageDigest.isEqual()` para tokens e hashes
4. **Zero stack trace exposto** — exceções genéricas para o cliente, detalhes apenas em log
5. **Log de todos os eventos de segurança** — com IP, fingerprint, userId, timestamp
6. **HTTPS obrigatório** — `requiresSecure()` no Spring Security ou reverse proxy
7. **Dependency scanning** — `mvn dependency-check:check` no CI (OWASP)
8. **Attacker-first:** sempre perguntar "como um atacante exploraria isso?" antes de implementar

---

## Dependências Core (pom.xml)

```xml
<!-- Spring Security -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>

<!-- JWT (jjwt 0.12.x) -->
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-api</artifactId>
    <version>0.12.5</version>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-impl</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>
<dependency>
    <groupId>io.jsonwebtoken</groupId>
    <artifactId>jjwt-jackson</artifactId>
    <version>0.12.5</version>
    <scope>runtime</scope>
</dependency>

<!-- Argon2id -->
<dependency>
    <groupId>org.bouncycastle</groupId>
    <artifactId>bcprov-jdk18on</artifactId>
    <version>1.78</version>
</dependency>

<!-- Rate Limiting -->
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-core</artifactId>
    <version>8.10.1</version>
</dependency>
<dependency>
    <groupId>com.bucket4j</groupId>
    <artifactId>bucket4j-redis</artifactId>
    <version>8.10.1</version>
</dependency>

<!-- Redis -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
<dependency>
    <groupId>org.redisson</groupId>
    <artifactId>redisson-spring-boot-starter</artifactId>
    <version>3.27.1</version>
</dependency>

<!-- Validation -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-validation</artifactId>
</dependency>

<!-- OWASP HTML Sanitizer -->
<dependency>
    <groupId>com.googlecode.owasp-java-html-sanitizer</groupId>
    <artifactId>owasp-java-html-sanitizer</artifactId>
    <version>20240325.1</version>
</dependency>

<!-- Thumbnailator (processamento de imagem seguro, sem ImageMagick) -->
<dependency>
    <groupId>net.coobird</groupId>
    <artifactId>thumbnailator</artifactId>
    <version>0.4.20</version>
</dependency>

<!-- CommonMark (Markdown seguro) -->
<dependency>
    <groupId>org.commonmark</groupId>
    <artifactId>commonmark</artifactId>
    <version>0.22.0</version>
</dependency>

<!-- Flying Saucer (PDF sem JS) -->
<dependency>
    <groupId>org.xhtmlrenderer</groupId>
    <artifactId>flying-saucer-pdf</artifactId>
    <version>9.4.0</version>
</dependency>

<!-- TOTP (2FA) -->
<dependency>
    <groupId>dev.samstevens.totp</groupId>
    <artifactId>totp-spring-boot-starter</artifactId>
    <version>1.7.1</version>
</dependency>
```

---

## Instruções de Uso desta Skill

1. **Identificar qual(is) camada(s) são relevantes** usando o Guia de Decisão Rápida
2. **Ler o(s) arquivo(s) de referência** correspondentes antes de gerar código
3. **Começar pelo vetor de ataque** descrito em cada camada — explicar para o usuário o que está sendo defendido
4. **Implementar com contexto do projeto do usuário** — adaptar nomes de classes, packages, e configs
5. **Incluir o checklist** da camada relevante no final da resposta

Para novos projetos: ler todas as camadas e implementar na ordem 1 → 13.
Para revisão de código: ler todas e auditar contra cada checklist.
