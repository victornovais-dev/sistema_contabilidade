# Layer 11 — Segurança de Renderização

> **Mindset pentester:** A aplicação que gera PDF, imagem, ou HTML dinamicamente com
> dados do usuário é um alvo enorme. HTML Injection em PDF engines vira SSRF.
> Markdown mal sanitizado vira XSS. Template engines com acesso ao contexto vazio viram RCE.
> Você gera algo? Você é responsável por tudo que pode estar dentro.

---

## Geração Segura de PDF

> **Ataque clássico:** PDF generators usam headless Chrome ou wkhtmltopdf.
> Injetar `<img src="http://169.254.169.254/latest/meta-data/">` vira SSRF.
> Injetar `<script>document.write(document.cookie)</script>` pode exfiltrar dados.

```java
// service/PdfGenerationService.java
@Service
public class PdfGenerationService {

    @Autowired private InputSanitizer sanitizer;
    @Autowired private SsrfValidator ssrfValidator;

    /**
     * Gerar PDF de forma segura — dados do usuário NUNCA chegam crus ao template.
     */
    public byte[] generateInvoicePdf(Invoice invoice) {
        // 1. Sanitizar TODOS os campos que virão do usuário antes do template
        InvoicePdfData sanitizedData = sanitizeInvoiceData(invoice);

        // 2. Usar template fixo com Thymeleaf — NUNCA concatenar HTML
        Context ctx = new Context();
        ctx.setVariable("invoice", sanitizedData);
        String html = templateEngine.process("invoice-pdf-template", ctx);

        // 3. Converter HTML → PDF com Flying Saucer (sem execução de JS)
        return convertHtmlToPdf(html);
    }

    private InvoicePdfData sanitizeInvoiceData(Invoice invoice) {
        return new InvoicePdfData(
            sanitizer.sanitizeText(invoice.getClientName()),
            sanitizer.sanitizeText(invoice.getDescription()),
            // Campos numéricos: validar tipo, não sanitizar texto
            validateMonetaryValue(invoice.getAmount()),
            // Datas: formatar explicitamente, nunca passar raw
            DateTimeFormatter.ISO_LOCAL_DATE.format(invoice.getDueDate())
        );
    }

    private byte[] convertHtmlToPdf(String safeHtml) {
        // Flying Saucer (iText) — renderiza HTML/CSS sem JavaScript
        // NÃO usar wkhtmltopdf ou headless Chrome (permitem JS → SSRF)
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ITextRenderer renderer = new ITextRenderer();
            renderer.setDocumentFromString(safeHtml);

            // Bloquear carregamento de recursos externos
            renderer.getSharedContext().setUserAgentCallback(new BlockingUserAgent());

            renderer.layout();
            renderer.createPDF(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Erro ao gerar PDF", e);
        }
    }
}

// Bloquear QUALQUER request externo durante renderização de PDF
class BlockingUserAgent extends NaiveUserAgent {
    @Override
    protected InputStream openConnection(String uri) {
        // Só permitir recursos do classpath/application
        if (uri.startsWith("classpath:") || uri.startsWith("data:")) {
            return super.openConnection(uri);
        }
        log.warn("PDF tentou acessar recurso externo: {}", uri);
        return null; // bloquear silenciosamente
    }
}
```

---

## Renderização Segura de Markdown

```java
// utils/MarkdownRenderer.java
@Component
public class MarkdownRenderer {

    private final Parser parser;
    private final HtmlRenderer renderer;
    private final PolicyFactory sanitizerPolicy;

    public MarkdownRenderer() {
        // CommonMark parser
        this.parser = Parser.builder().build();

        // Renderer com sanitização de HTML inline desabilitada
        this.renderer = HtmlRenderer.builder()
            .escapeHtml(true) // escapar HTML raw que usuário tentar injetar
            .softbreak("<br />")
            .build();

        // Política OWASP para o HTML gerado pelo Markdown
        this.sanitizerPolicy = new HtmlPolicyBuilder()
            .allowElements("p", "br", "b", "i", "em", "strong", "ul", "ol", "li",
                           "h1", "h2", "h3", "h4", "blockquote", "code", "pre",
                           "hr", "table", "thead", "tbody", "tr", "th", "td")
            .allowAttributes("href").onElements("a")
            .allowAttributes("src", "alt", "width", "height").onElements("img")
            // Links: apenas protocolos seguros
            .allowStandardUrlProtocols()
            // Links: sempre abre em nova aba E noopener noreferrer
            .requireRelNofollowOnLinks()
            .toFactory();
    }

    public String render(String markdownInput) {
        if (markdownInput == null) return "";

        // 1. Limitar tamanho antes de parsear
        if (markdownInput.length() > 50_000) {
            throw new InvalidInputException("Conteúdo muito longo");
        }

        // 2. Parsear Markdown → HTML
        Node document = parser.parse(markdownInput);
        String rawHtml = renderer.render(document);

        // 3. Sanitizar o HTML gerado (remove qualquer XSS que sobreviveu)
        String sanitized = sanitizerPolicy.sanitize(rawHtml);

        // 4. Adicionar target="_blank" e rel="noopener noreferrer" em todos os links
        return sanitized.replace("<a ", "<a target=\"_blank\" rel=\"noopener noreferrer nofollow\" ");
    }
}
```

---

## Proteção contra Template Injection (SSTI)

> **Ataque:** Se o template recebe input do usuário como parte do NOME do template ou
> como expressão Thymeleaf/FreeMarker, é possível executar código no servidor.
> `${T(java.lang.Runtime).getRuntime().exec('id')}` — RCE clássico em Spring/Thymeleaf.

```java
// NUNCA fazer:
String templateName = "user-" + userInput + "-template";  // ← template injection
templateEngine.process(templateName, context);             // RCE se userInput = "../../../etc/passwd"

// NUNCA fazer:
context.setVariable("expression", "${T(Runtime).exec(userInput)}"); // ← SSTI

// FAZER: nomes de template fixos, hardcoded no código
private static final Map<String, String> ALLOWED_TEMPLATES = Map.of(
    "invoice", "templates/invoice-pdf",
    "receipt", "templates/receipt-pdf",
    "report",  "templates/monthly-report"
);

public byte[] generateDocument(String documentType, Object data) {
    String templateName = ALLOWED_TEMPLATES.get(documentType.toLowerCase());
    if (templateName == null) {
        throw new InvalidInputException("Tipo de documento inválido");
    }
    // Agora seguro — templateName vem de whitelist, não do usuário
    return process(templateName, data);
}
```

---

## Geração Segura de Imagens

```java
// service/ImageProcessingService.java
@Service
public class ImageProcessingService {

    @Value("${image.max-dimension:2048}")
    private int maxDimension;

    /**
     * Redimensionar imagem de forma segura.
     * Protege contra ImageMagick vulnerabilities (CVE-2016-3714 - ImageTragick).
     */
    public byte[] resizeImage(byte[] inputBytes, int targetWidth, int targetHeight) {
        // Usar Thumbnailator (Java puro) — sem ImageMagick
        // ImageMagick processa SVG, PDF, etc. e tem histórico de CVEs graves
        try (ByteArrayInputStream in = new ByteArrayInputStream(inputBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            // Verificar dimensões antes de processar
            BufferedImage img = ImageIO.read(in);
            if (img == null) throw new InvalidFileException("Imagem inválida");
            if (img.getWidth() > maxDimension || img.getHeight() > maxDimension) {
                throw new InvalidFileException("Imagem muito grande para processamento");
            }

            // Limitar target dimensions
            int safeWidth = Math.min(targetWidth, maxDimension);
            int safeHeight = Math.min(targetHeight, maxDimension);

            Thumbnails.of(img)
                .size(safeWidth, safeHeight)
                .outputFormat("jpeg") // normalizar para JPEG — elimina metadados e polimorfismo
                .outputQuality(0.85)
                .toOutputStream(out);

            return out.toByteArray();
        } catch (IOException e) {
            throw new InvalidFileException("Erro ao processar imagem");
        }
    }

    /**
     * Remover metadados EXIF (podem conter localização GPS e outros dados sensíveis).
     */
    public byte[] stripExifData(byte[] imageBytes) {
        try (ByteArrayInputStream in = new ByteArrayInputStream(imageBytes);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {

            BufferedImage img = ImageIO.read(in);
            // Reescrever como JPEG puro — EXIF não é copiado
            ImageIO.write(img, "jpeg", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new RuntimeException("Erro ao remover EXIF", e);
        }
    }
}
```

---

## Renderização Segura no Frontend (React/Angular)

```typescript
// NUNCA:
element.innerHTML = userContent;                    // XSS direto
document.write(userContent);                        // XSS direto
React: <div dangerouslySetInnerHTML={{ __html: data.bio }} />  // XSS se não sanitizado

// SEMPRE:
element.textContent = userContent;                  // escaping automático
React: <div>{data.bio}</div>                        // React escapa automaticamente

// Quando precisar renderizar HTML (rich text sanitizado pelo backend):
import DOMPurify from 'dompurify';

function SafeRichText({ html }: { html: string }) {
    const clean = DOMPurify.sanitize(html, {
        ALLOWED_TAGS: ['p', 'b', 'i', 'em', 'strong', 'ul', 'ol', 'li', 'br', 'a'],
        ALLOWED_ATTR: ['href', 'rel', 'target'],
        FORCE_BODY: true,
        ADD_ATTR: ['target'],  // forçar target="_blank"
    });

    // Garantir noopener em todos os links
    const withNoopener = clean.replace(/<a /g, '<a rel="noopener noreferrer" ');

    return <div dangerouslySetInnerHTML={{ __html: withNoopener }} />;
}
```

---

## Checklist Layer 11

- [ ] PDF gerado com Flying Saucer (sem JS) — nunca wkhtmltopdf/headless Chrome
- [ ] PDF: `BlockingUserAgent` impedindo requests externos durante renderização
- [ ] Dados do usuário sanitizados ANTES de entrar no template PDF
- [ ] Markdown renderizado com `escapeHtml=true` + sanitização OWASP do output
- [ ] Links em Markdown: `target="_blank" rel="noopener noreferrer nofollow"`
- [ ] Nomes de templates fixos via whitelist — nunca derivados de input
- [ ] SSTI: expressões de template nunca contêm dados do usuário
- [ ] Imagens processadas com Thumbnailator (Java puro) — sem ImageMagick
- [ ] EXIF removido de todas as imagens antes de armazenar/servir
- [ ] Frontend: `textContent` em vez de `innerHTML` — sempre
- [ ] Frontend: DOMPurify em QUALQUER uso de `dangerouslySetInnerHTML`
- [ ] Frontend: `noopener noreferrer` em links externos
