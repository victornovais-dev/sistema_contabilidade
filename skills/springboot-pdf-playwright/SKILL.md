---
name: springboot-pdf-playwright
description: >
  Gera relatórios PDF em Java Spring Boot usando Playwright para renderizar templates HTML e exportar como PDF.
  Use este skill sempre que o usuário quiser: gerar PDFs a partir de templates HTML no Spring Boot, usar Playwright
  como engine de PDF em Java, criar relatórios financeiros/gerenciais em PDF com dados dinâmicos, configurar
  dependência playwright-java, renderizar HTML com Thymeleaf ou strings e converter para PDF via headless Chromium.
  Trigger obrigatório quando houver qualquer combinação de: Spring Boot + PDF, Playwright + Java, HTML template +
  geração de relatório, ou menção ao template de relatório financeiro do projeto.
---

# Spring Boot PDF com Playwright

Geração de PDFs a partir de templates HTML usando `playwright-java` (headless Chromium) no Spring Boot.

## Visão geral da abordagem

```
ReportData (POJO)
    → TemplateEngine (Thymeleaf ou string builder)
    → HTML string com dados injetados
    → PlaywrightPdfService
        → Browser.newPage()
        → page.setContent(html)
        → page.pdf(options A4)
    → byte[] PDF
    → ResponseEntity<byte[]> (download)
```

## Dependências (pom.xml)

```xml
<!-- Playwright -->
<dependency>
    <groupId>com.microsoft.playwright</groupId>
    <artifactId>playwright</artifactId>
    <version>1.44.0</version>
</dependency>

<!-- Thymeleaf (template engine opcional mas recomendado) -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-thymeleaf</artifactId>
</dependency>
```

> **Primeira execução**: o Playwright baixa o Chromium automaticamente via `playwright install chromium`.
> Em ambiente Docker/CI, adicione o step de instalação — veja `references/docker-setup.md`.

---

## Estrutura de arquivos a gerar

```
src/
├── main/
│   ├── java/.../
│   │   ├── report/
│   │   │   ├── ReportData.java          # POJO com os dados do relatório
│   │   │   ├── PlaywrightPdfService.java # Serviço de renderização
│   │   │   └── ReportController.java    # Endpoint REST
│   │   └── config/
│   │       └── PlaywrightConfig.java    # Bean singleton do Browser
│   └── resources/
│       └── templates/
│           └── relatorio-financeiro.html # Template Thymeleaf
```

---

## 1. PlaywrightConfig.java — Bean singleton

```java
@Configuration
public class PlaywrightConfig {

    @Bean(destroyMethod = "close")
    public Playwright playwright() {
        return Playwright.create();
    }

    @Bean(destroyMethod = "close")
    public Browser browser(Playwright playwright) {
        return playwright.chromium().launch(
            new BrowserType.LaunchOptions().setHeadless(true)
        );
    }
}
```

> Use **singleton** — criar um `Browser` por requisição é caro (±1 s de startup).

---

## 2. PlaywrightPdfService.java

```java
@Service
@RequiredArgsConstructor
public class PlaywrightPdfService {

    private final Browser browser;
    private final ThymeleafTemplateRenderer renderer; // veja seção 3

    public byte[] generatePdf(ReportData data) {
        String html = renderer.render("relatorio-financeiro", data);

        try (BrowserContext ctx = browser.newContext();
             Page page = ctx.newPage()) {

            page.setContent(html, new Page.SetContentOptions()
                .setWaitUntil(WaitUntilState.NETWORKIDLE));

            return page.pdf(new Page.PdfOptions()
                .setFormat("A4")
                .setPrintBackground(true)
                .setMargin(new Margin()
                    .setTop("0mm")
                    .setBottom("0mm")
                    .setLeft("0mm")
                    .setRight("0mm")));
        }
    }
}
```

**Notas críticas:**
- `NETWORKIDLE` garante que fontes e recursos inline carreguem antes do PDF.
- `setPrintBackground(true)` é obrigatório para gradientes e cores de fundo renderizarem.
- Margem `0mm` porque o template já define `padding` interno na `.page`.
- `BrowserContext` e `Page` são fechados por `try-with-resources` — thread-safe.

---

## 3. ThymeleafTemplateRenderer.java

```java
@Component
@RequiredArgsConstructor
public class ThymeleafTemplateRenderer {

    private final TemplateEngine templateEngine;

    public String render(String templateName, Object data) {
        Context ctx = new Context();
        ctx.setVariable("report", data);
        return templateEngine.process(templateName, ctx);
    }
}
```

---

## 4. ReportData.java — POJO de exemplo (relatório financeiro)

Adapte os campos conforme os dados reais do projeto:

```java
@Data
@Builder
public class ReportData {
    private String empresa;
    private String periodoInicio;
    private String periodoFim;
    private String responsavel;
    private String dataEmissao;

    private BigDecimal totalReceitas;
    private BigDecimal totalDespesas;
    private BigDecimal saldoFinal;

    private List<LancamentoItem> receitas;
    private List<LancamentoItem> despesas;
    private List<CategoriaItem> categorias; // para o gráfico / legenda
    private String observacoes;

    @Data
    @Builder
    public static class LancamentoItem {
        private String data;
        private String descricao;
        private String categoria;
        private BigDecimal valor;
    }

    @Data
    @Builder
    public static class CategoriaItem {
        private String nome;
        private String cor;        // hex, ex: "#2563eb"
        private BigDecimal valor;
        private double percentual;
    }
}
```

---

## 5. Template Thymeleaf (relatorio-financeiro.html)

Adapte o template base — veja `assets/relatorio-financeiro.html` — substituindo os
valores hardcoded por expressões Thymeleaf:

```html
<!-- Cabeçalho meta -->
<span class="meta-value" th:text="${report.empresa}">Nome da Empresa</span>
<span class="meta-value" th:text="${report.periodoInicio + ' a ' + report.periodoFim}">01/03/2026 a 31/03/2026</span>
<span class="meta-value" th:text="${report.responsavel}">Financeiro</span>
<span class="meta-value" th:text="${report.dataEmissao}">01/04/2026</span>

<!-- Cards de resumo -->
<div class="summary-value" th:text="${#numbers.formatDecimal(report.totalReceitas, 1, 'POINT', 2, 'COMMA')}">R$ 18.750,00</div>
<div class="summary-value" th:text="${#numbers.formatDecimal(report.totalDespesas, 1, 'POINT', 2, 'COMMA')}">R$ 11.420,00</div>
<div class="summary-value" th:text="${#numbers.formatDecimal(report.saldoFinal, 1, 'POINT', 2, 'COMMA')}">R$ 7.330,00</div>

<!-- Tabela de receitas -->
<tr th:each="item : ${report.receitas}">
    <td th:text="${item.data}"></td>
    <td th:text="${item.descricao}"></td>
    <td th:text="${item.categoria}"></td>
    <td class="text-right value-income"
        th:text="'R$ ' + ${#numbers.formatDecimal(item.valor, 1, 'POINT', 2, 'COMMA')}"></td>
</tr>

<!-- Tabela de despesas -->
<tr th:each="item : ${report.despesas}">
    <td th:text="${item.data}"></td>
    <td th:text="${item.descricao}"></td>
    <td th:text="${item.categoria}"></td>
    <td class="text-right value-expense"
        th:text="'R$ ' + ${#numbers.formatDecimal(item.valor, 1, 'POINT', 2, 'COMMA')}"></td>
</tr>

<!-- Legenda de categorias -->
<div class="legend-item" th:each="cat : ${report.categorias}">
    <span class="legend-dot" th:style="'background:' + ${cat.cor} + ';'"></span>
    <span class="legend-label" th:text="${cat.nome}"></span>
    <span class="legend-value"
          th:text="'R$ ' + ${#numbers.formatDecimal(cat.valor,1,'POINT',2,'COMMA')} + ' · ' + ${#numbers.formatDecimal(cat.percentual,1,'POINT',2,'COMMA')} + '%'"></span>
</div>
```

> O gráfico SVG (donut) precisa ter os `stroke-dasharray` calculados dinamicamente.
> Veja a seção **Gráfico SVG dinâmico** em `references/svg-chart.md`.

---

## 6. ReportController.java

```java
@RestController
@RequestMapping("/api/relatorios")
@RequiredArgsConstructor
public class ReportController {

    private final PlaywrightPdfService pdfService;

    @PostMapping(value = "/financeiro/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> gerarRelatorioFinanceiro(
            @RequestBody ReportData data) {

        byte[] pdf = pdfService.generatePdf(data);

        String filename = "relatorio-financeiro-%s.pdf"
            .formatted(LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd")));

        return ResponseEntity.ok()
            .header(HttpHeaders.CONTENT_DISPOSITION,
                    "attachment; filename=\"" + filename + "\"")
            .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE)
            .body(pdf);
    }
}
```

---

## Checklist de implementação

- [ ] Adicionar dependências no `pom.xml`
- [ ] Criar `PlaywrightConfig` (bean singleton)
- [ ] Criar `PlaywrightPdfService`
- [ ] Criar `ThymeleafTemplateRenderer`
- [ ] Criar `ReportData` com os campos reais
- [ ] Adaptar `relatorio-financeiro.html` com expressões Thymeleaf
- [ ] Criar `ReportController`
- [ ] Testar geração local (primeiro run baixa Chromium ~100 MB)
- [ ] Configurar Docker se necessário — ver `references/docker-setup.md`

---

## Referências

- `references/docker-setup.md` — Dockerfile e configuração para CI/produção
- `references/svg-chart.md` — Cálculo dinâmico do gráfico donut SVG
- `assets/relatorio-financeiro.html` — Template HTML base original
