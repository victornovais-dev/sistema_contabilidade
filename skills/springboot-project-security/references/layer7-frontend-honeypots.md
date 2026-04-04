# Layer 7 — Honeypots no Frontend

> **Mindset pentester:** Bots e scanners são previsíveis. Eles preenchem todos os campos
> de formulário, seguem todos os links, não executam CSS corretamente, e têm timing
> suspeito. Honeypots no frontend detectam isso antes mesmo de um request chegar ao backend.

---

## Técnica 1 — Campo Honeypot Invisível (Anti-Bot Clássico)

Bots preenchem TODOS os campos de formulário. Humanos não veem campos ocultos por CSS.

```html
<!-- login.html / login component -->
<form id="loginForm">
    <!-- Campos reais -->
    <input type="email" name="email" required />
    <input type="password" name="password" required />

    <!--
        CAMPO HONEYPOT — invisível por CSS, não por type="hidden"
        (bots detectam type="hidden" e ignoram; CSS eles não entendem)
        name="username" parece legítimo para um bot
    -->
    <div class="field-trap" aria-hidden="true" tabindex="-1">
        <label for="username">Username</label>
        <input
            type="text"
            id="username"
            name="username"
            autocomplete="off"
            tabindex="-1"
        />
    </div>
</form>
```

```css
/* CSS — campo completamente invisível mas presente no DOM */
.field-trap {
    position: absolute;
    left: -9999px;
    top: -9999px;
    width: 1px;
    height: 1px;
    overflow: hidden;
    opacity: 0;
    pointer-events: none;
}
```

```typescript
// Verificar no submit — enviar flag para o backend
loginForm.addEventListener('submit', (e) => {
    e.preventDefault();

    const honeypotValue = (document.getElementById('username') as HTMLInputElement).value;

    const payload = {
        email: formData.email,
        password: formData.password,
        _hpf: honeypotValue,                    // honeypot field value
        _ts: Date.now() - formLoadTime,          // tempo para preencher (ms)
        _mv: mouseMovements > 0,                 // houve movimentos de mouse?
        _kc: keystrokeCount,                     // número de teclas pressionadas
    };

    // Se o honeypot tiver valor, o backend vai saber que é bot
    await authService.login(payload);
});
```

```java
// Backend: verificar flags de bot
@PostMapping("/login")
public ResponseEntity<?> login(@RequestBody LoginRequest req) {

    BotSignals signals = BotSignals.from(req);

    if (signals.isBot()) {
        // Registrar silenciosamente — não revelar detecção
        SecurityEventPublisher.publish(SecurityEvent.BOT_DETECTED, signals.toString());

        // Simular delay e retornar erro genérico (não revelar que foi detectado)
        Thread.sleep(new SecureRandom().nextInt(2000) + 500);
        return ResponseEntity.ok(Map.of("error", "Credenciais inválidas"));
    }

    return authService.login(req);
}

record BotSignals(boolean honeypotFilled, long formFillTime, boolean hasMouseMovement, int keystrokeCount) {
    static BotSignals from(LoginRequest req) {
        return new BotSignals(
            req.getHpf() != null && !req.getHpf().isBlank(),  // bot preencheu honeypot
            req.getTs() != null ? req.getTs() : 0,
            req.getMv() != null && req.getMv(),
            req.getKc() != null ? req.getKc() : 0
        );
    }

    boolean isBot() {
        if (honeypotFilled) return true;
        if (formFillTime < 800) return true;          // < 800ms — impossível para humano
        if (!hasMouseMovement && keystrokeCount > 5) return true; // digitou sem mexer o mouse
        return false;
    }
}
```

---

## Técnica 2 — Timing de Preenchimento do Formulário

```typescript
// Medir comportamento humano
class FormBehaviorTracker {
    private formLoadTime: number = Date.now();
    private mouseMovements: number = 0;
    private keystrokeCount: number = 0;
    private focusEvents: string[] = [];

    init(formElement: HTMLFormElement): void {
        // Rastrear movimentos de mouse
        document.addEventListener('mousemove', () => this.mouseMovements++, { passive: true });

        // Rastrear teclas
        formElement.addEventListener('keydown', () => this.keystrokeCount++);

        // Rastrear sequência de focus (bots seguem tab order perfeitamente)
        formElement.querySelectorAll('input').forEach(input => {
            input.addEventListener('focus', () => this.focusEvents.push(input.name));
        });
    }

    getSignals() {
        return {
            fillTimeMs: Date.now() - this.formLoadTime,
            mouseMovements: this.mouseMovements,
            keystrokeCount: this.keystrokeCount,
            // Bots geralmente focam campos em ordem exata — humanos pulam
            focusSequence: this.focusEvents.join(',')
        };
    }
}
```

---

## Técnica 3 — Link Honeypot Invisível (Detectar Crawlers)

```html
<!-- Em qualquer página da aplicação -->
<!-- Invisível para humanos, crawlers seguem todos os links -->
<a
    href="/api/crawler-trap/secret-resource-7f3k2"
    style="display:none; color: transparent; font-size: 0"
    aria-hidden="true"
    tabindex="-1"
><!-- trap --></a>
```

```java
// Backend: qualquer acesso a esta URL = crawler/scanner automatizado
@GetMapping("/api/crawler-trap/{id}")
public ResponseEntity<?> crawlerTrap(@PathVariable String id, HttpServletRequest req) {
    // Logar o crawler
    SecurityEventPublisher.publish(SecurityEvent.CRAWLER_DETECTED,
        "IP: " + fingerprintService.getClientIp(req));

    // Registrar IP para monitoramento
    crawlerBlacklist.register(fingerprintService.getClientIp(req));

    // Retornar 200 com conteúdo fake — não revelar que foi detectado
    return ResponseEntity.ok(Map.of("data", List.of(), "page", 1, "total", 0));
}
```

---

## Técnica 4 — Canvas Fingerprinting para Detecção de Headless Browser

```typescript
// Detectar Puppeteer, Playwright, Selenium antes de submeter qualquer form
class HeadlessDetector {

    static async detect(): Promise<boolean> {
        const signals: boolean[] = [];

        // 1. Canvas fingerprint — headless browsers têm canvas diferente
        signals.push(this.checkCanvasSupport());

        // 2. WebGL — Puppeteer sem GPU tem valores específicos
        signals.push(this.checkWebGLVendor());

        // 3. Plugins — browsers headless não têm plugins normalmente
        signals.push(navigator.plugins.length === 0);

        // 4. Automated property — exposta por alguns headless
        signals.push((navigator as any).webdriver === true);

        // 5. Inconsistência de timezone
        signals.push(this.checkTimezoneConsistency());

        // Se 3+ sinais positivos — provavelmente headless
        const headlessSignals = signals.filter(Boolean).length;
        return headlessSignals >= 3;
    }

    private static checkCanvasSupport(): boolean {
        try {
            const canvas = document.createElement('canvas');
            const ctx = canvas.getContext('2d');
            if (!ctx) return true; // sem canvas = headless

            ctx.fillText('test', 10, 10);
            const dataUrl = canvas.toDataURL();
            return dataUrl === 'data:,'; // canvas vazio = headless
        } catch {
            return true;
        }
    }

    private static checkWebGLVendor(): boolean {
        try {
            const canvas = document.createElement('canvas');
            const gl = canvas.getContext('webgl') as WebGLRenderingContext;
            if (!gl) return true;

            const debugInfo = gl.getExtension('WEBGL_debug_renderer_info');
            if (!debugInfo) return false;

            const vendor = gl.getParameter(debugInfo.UNMASKED_VENDOR_WEBGL) as string;
            // Puppeteer/headless Chrome reporta vendor específico
            return vendor?.includes('SwiftShader') || vendor?.includes('Google');
        } catch {
            return false;
        }
    }

    private static checkTimezoneConsistency(): boolean {
        const offset = new Date().getTimezoneOffset();
        const browserTz = Intl.DateTimeFormat().resolvedOptions().timeZone;
        // Inconsistência entre offset e timezone name = manipulação
        return !browserTz && offset !== 0;
    }
}

// Uso antes de qualquer ação sensível
const isHeadless = await HeadlessDetector.detect();
if (isHeadless) {
    // Enviar flag para backend — não bloquear no frontend (fácil de bypassar)
    payload._headless = true;
}
```

---

## Técnica 5 — Pixel de Rastreamento para Exfiltração de Dados

```typescript
// Detectar se o usuário está usando um proxy que não executa JS
// (scanners passivos que só fazem GET)
// Injeta um "pixel" que faz beacon para confirmar que JS executou

// No carregamento da app:
const sessionToken = crypto.randomUUID();
sessionStorage.setItem('_st', sessionToken); // temporário — só para rastrear esta sessão

// Fazer beacon para confirmar execução de JS
navigator.sendBeacon('/api/ping', JSON.stringify({
    _st: sessionToken,
    _ua: navigator.userAgent,
    _lang: navigator.language,
    _tz: Intl.DateTimeFormat().resolvedOptions().timeZone
}));
```

---

## Proteção contra Automated Form Submission com CAPTCHA Invisível

```typescript
// Google reCAPTCHA v3 — sem interação do usuário, score de 0.0 a 1.0
declare const grecaptcha: any;

async function getRecaptchaToken(action: string): Promise<string> {
    return new Promise((resolve) => {
        grecaptcha.ready(() => {
            grecaptcha.execute('RECAPTCHA_SITE_KEY', { action })
                .then(resolve);
        });
    });
}

// No submit de login:
const recaptchaToken = await getRecaptchaToken('login');
payload._recaptcha = recaptchaToken;
```

```java
// Backend: verificar score do reCAPTCHA
@Service
public class RecaptchaService {
    @Value("${recaptcha.secret-key}")
    private String secretKey;

    public boolean verify(String token, float minScore) {
        // Verificar com API do Google
        RestTemplate rt = new RestTemplate();
        String url = "https://www.google.com/recaptcha/api/siteverify?secret={secret}&response={token}";
        Map<String, Object> response = rt.getForObject(url, Map.class, secretKey, token);

        if (response == null || !Boolean.TRUE.equals(response.get("success"))) return false;

        float score = ((Number) response.getOrDefault("score", 0)).floatValue();
        return score >= minScore; // 0.5 para login, 0.7 para ações críticas
    }
}
```

---

## Checklist Layer 7

- [ ] Campo honeypot em TODOS os formulários (invisible via CSS, não type="hidden")
- [ ] Timing de preenchimento verificado no backend (< 800ms = bot)
- [ ] Rastreamento de movimentos de mouse e teclas
- [ ] Link honeypot em páginas públicas (detectar crawlers)
- [ ] Detecção de headless browser (navigator.webdriver, canvas, WebGL)
- [ ] Sinais de bot enviados para o backend como flags — bloqueio serverside
- [ ] Bot detectado → delay aleatório + resposta genérica (não revelar detecção)
- [ ] reCAPTCHA v3 em endpoints críticos (login, cadastro, recuperação)
- [ ] Score mínimo configurável por endpoint (0.5 login, 0.7 ações críticas)
