package com.sistema_contabilidade.relatorio.service;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.microsoft.playwright.Page.PdfOptions;
import com.microsoft.playwright.Page.SetContentOptions;
import com.microsoft.playwright.options.Margin;
import com.microsoft.playwright.options.WaitUntilState;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import java.io.IOException;
import java.util.Base64;
import java.util.Map;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class PlaywrightPdfService {

  private static final String PDF_FOOTER_TEMPLATE =
      """
      <div style="
          width: 100%;
          background: #ffffff;
        ">
        <div style="
          width: 100%;
          font-size: 10px;
          color: #64748b;
          padding: 0 18mm;
          box-sizing: border-box;
          font-family: Inter, 'Segoe UI', Arial, sans-serif;
        ">
          <div style="
              width: 100%;
              border-top: 1px solid #e2e8f0;
              padding-top: 8px;
              display: flex;
              justify-content: flex-end;
              align-items: center;
              gap: 12px;
              background: #ffffff;
            ">
            <span>Pagina <span class="pageNumber"></span> de <span class="totalPages"></span></span>
          </div>
        </div>
      </div>
      """;

  private static final String PDF_HEADER_TEMPLATE =
      """
      <div style="width: 100%; background: #ffffff;"></div>
      """;
  private static final String LOGO_RESOURCE_PATH = "static/assets/img/Sacs Contábil_logo_azul.png";

  private final Browser browser;
  private final ThymeleafTemplateRenderer templateRenderer;

  public PlaywrightPdfService(Browser browser, ThymeleafTemplateRenderer templateRenderer) {
    this.browser = browser;
    this.templateRenderer = templateRenderer;
  }

  public byte[] generateFinancialReportPdf(RelatorioFinanceiroPdfData data) {
    String logoDataUri = loadLogoDataUri();
    String html =
        templateRenderer.render(
            "relatorio-financeiro", "report", data, Map.of("logoDataUri", logoDataUri));
    try (BrowserContext browserContext = browser.newContext();
        Page page = browserContext.newPage()) {
      page.setContent(html, new SetContentOptions().setWaitUntil(WaitUntilState.NETWORKIDLE));
      page.waitForFunction("window.__reportLayoutReady === true");
      return page.pdf(
          new PdfOptions()
              .setFormat("A4")
              .setDisplayHeaderFooter(true)
              .setHeaderTemplate(PDF_HEADER_TEMPLATE)
              .setFooterTemplate(PDF_FOOTER_TEMPLATE)
              .setPrintBackground(true)
              .setMargin(
                  new Margin().setTop("0mm").setRight("0mm").setBottom("18mm").setLeft("0mm")));
    } catch (RuntimeException exception) {
      throw new IllegalStateException("Falha ao gerar PDF com Playwright", exception);
    }
  }

  private String loadLogoDataUri() {
    try {
      ClassPathResource resource = new ClassPathResource(LOGO_RESOURCE_PATH);
      byte[] bytes = resource.getInputStream().readAllBytes();
      return "data:image/png;base64," + Base64.getEncoder().encodeToString(bytes);
    } catch (IOException exception) {
      throw new IllegalStateException("Falha ao carregar logo do relatorio PDF", exception);
    }
  }
}
