package com.sistema_contabilidade.relatorio.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.microsoft.playwright.Browser;
import com.microsoft.playwright.BrowserContext;
import com.microsoft.playwright.Page;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

@DisplayName("PlaywrightPdfService unit tests")
class PlaywrightPdfServiceTest {

  @Test
  @DisplayName("Deve gerar PDF a partir do HTML renderizado")
  void deveGerarPdfAPartirDoHtmlRenderizado() {
    Browser browser = Mockito.mock(Browser.class);
    BrowserContext browserContext = Mockito.mock(BrowserContext.class);
    Page page = Mockito.mock(Page.class);
    ThymeleafTemplateRenderer templateRenderer = Mockito.mock(ThymeleafTemplateRenderer.class);
    PlaywrightPdfService service = new PlaywrightPdfService(browser, templateRenderer);
    RelatorioFinanceiroPdfData data = sampleData();
    byte[] pdf = "pdf".getBytes(StandardCharsets.UTF_8);

    when(templateRenderer.render(
            eq("relatorio-financeiro"), eq("report"), eq(data), any(Map.class)))
        .thenReturn("<html/>");
    when(browser.newContext()).thenReturn(browserContext);
    when(browserContext.newPage()).thenReturn(page);
    when(page.pdf(any(Page.PdfOptions.class))).thenReturn(pdf);

    byte[] result = service.generateFinancialReportPdf(data);

    assertArrayEquals(pdf, result);
    verify(page).setContent(eq("<html/>"), any(Page.SetContentOptions.class));
    verify(page).waitForFunction("window.__reportLayoutReady === true");
    verify(page).pdf(any(Page.PdfOptions.class));
    verify(browserContext).close();
  }

  @Test
  @DisplayName("Deve encapsular falha do Playwright ao gerar PDF")
  void deveEncapsularFalhaDoPlaywrightAoGerarPdf() {
    Browser browser = Mockito.mock(Browser.class);
    ThymeleafTemplateRenderer templateRenderer = Mockito.mock(ThymeleafTemplateRenderer.class);
    PlaywrightPdfService service = new PlaywrightPdfService(browser, templateRenderer);
    RelatorioFinanceiroPdfData data = sampleData();

    when(templateRenderer.render(
            eq("relatorio-financeiro"), eq("report"), eq(data), any(Map.class)))
        .thenReturn("<html/>");
    when(browser.newContext()).thenThrow(new RuntimeException("playwright"));

    IllegalStateException exception =
        assertThrows(IllegalStateException.class, () -> service.generateFinancialReportPdf(data));

    org.junit.jupiter.api.Assertions.assertEquals(
        "Falha ao gerar PDF com Playwright", exception.getMessage());
  }

  private RelatorioFinanceiroPdfData sampleData() {
    return new RelatorioFinanceiroPdfData(
        "Sistema Contabilidade",
        "03/04/2026 a 04/04/2026",
        "Maria Silva",
        "04/04/2026 16:27",
        "R$ 10,00",
        "R$ 5,00",
        "R$ 5,00",
        "Relatorio de teste",
        "Observacoes de teste",
        List.of(),
        List.of(),
        List.of(),
        List.of());
  }
}
