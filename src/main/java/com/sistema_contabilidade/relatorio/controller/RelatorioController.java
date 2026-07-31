package com.sistema_contabilidade.relatorio.controller;

import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroPdfData;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import com.sistema_contabilidade.relatorio.exception.PdfGenerationCapacityExceededException;
import com.sistema_contabilidade.relatorio.service.PdfGenerationLimiter;
import com.sistema_contabilidade.relatorio.service.RelatorioFinanceiroService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/relatorios")
@Validated
@RequiredArgsConstructor
public class RelatorioController {

  private final RelatorioFinanceiroService relatorioFinanceiroService;
  private final PdfGenerationLimiter pdfGenerationLimiter;

  @GetMapping("/financeiro")
  public ResponseEntity<RelatorioFinanceiroResumoResponse> obterRelatorioFinanceiro(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return ResponseEntity.ok(relatorioFinanceiroService.gerarResumo(authentication, role));
  }

  @GetMapping(value = "/financeiro/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> baixarRelatorioFinanceiroPdf(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return pdfGenerationLimiter.execute(() -> gerarRelatorioFinanceiroPdf(authentication, role));
  }

  private ResponseEntity<byte[]> gerarRelatorioFinanceiroPdf(
      Authentication authentication, String role) {
    RelatorioFinanceiroResponse relatorio = relatorioFinanceiroService.gerar(authentication, role);
    RelatorioFinanceiroPdfData dadosPdf =
        relatorioFinanceiroService.prepararDadosPdf(authentication, relatorio);
    byte[] payload = relatorioFinanceiroService.gerarPdf(dadosPdf);
    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"relatorio-financeiro.pdf\"")
        .contentType(MediaType.APPLICATION_PDF)
        .body(payload);
  }

  @GetMapping("/roles")
  public ResponseEntity<List<String>> listarRolesDisponiveis(Authentication authentication) {
    return ResponseEntity.ok(relatorioFinanceiroService.listarRolesDisponiveis(authentication));
  }

  @ExceptionHandler(PdfGenerationCapacityExceededException.class)
  public ResponseEntity<Void> handlePdfGenerationCapacityExceeded(
      PdfGenerationCapacityExceededException exception) {
    return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
        .header(HttpHeaders.RETRY_AFTER, Long.toString(exception.getRetryAfterSeconds()))
        .build();
  }
}
