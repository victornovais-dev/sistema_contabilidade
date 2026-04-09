package com.sistema_contabilidade.relatorio.controller;

import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResponse;
import com.sistema_contabilidade.relatorio.service.RelatorioFinanceiroService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
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

  @GetMapping("/financeiro")
  public ResponseEntity<RelatorioFinanceiroResponse> obterRelatorioFinanceiro(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return ResponseEntity.ok(relatorioFinanceiroService.gerar(authentication, role));
  }

  @GetMapping(value = "/financeiro/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
  public ResponseEntity<byte[]> baixarRelatorioFinanceiroPdf(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    RelatorioFinanceiroResponse relatorio = relatorioFinanceiroService.gerar(authentication, role);
    byte[] payload = relatorioFinanceiroService.gerarPdf(authentication, relatorio);
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
}
