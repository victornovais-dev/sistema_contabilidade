package com.sistema_contabilidade.privacidade.controller;

import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeConsultaRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeConsultaResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeCreateRequest;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeCreateResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeListResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeResponse;
import com.sistema_contabilidade.privacidade.dto.SolicitacaoPrivacidadeUpdateRequest;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeStatus;
import com.sistema_contabilidade.privacidade.model.SolicitacaoPrivacidadeTipo;
import com.sistema_contabilidade.privacidade.service.SolicitacaoPrivacidadeService;
import com.sistema_contabilidade.security.util.SecurityPaths;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityPaths.PRIVACY_REQUESTS_API_BASE)
@RequiredArgsConstructor
public class SolicitacaoPrivacidadeController {

  private static final String ADMIN_ROLE_EXPRESSION = "hasRole('ADMIN')";
  private final SolicitacaoPrivacidadeService solicitacaoService;

  @PostMapping
  public ResponseEntity<SolicitacaoPrivacidadeCreateResponse> registrar(
      @Valid @RequestBody SolicitacaoPrivacidadeCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED)
        .cacheControl(CacheControl.noStore())
        .body(solicitacaoService.registrar(request));
  }

  @PostMapping("/consulta")
  public ResponseEntity<SolicitacaoPrivacidadeConsultaResponse> consultar(
      @Valid @RequestBody SolicitacaoPrivacidadeConsultaRequest request) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(solicitacaoService.consultar(request));
  }

  @GetMapping
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public ResponseEntity<SolicitacaoPrivacidadeListResponse> listar(
      @RequestParam(defaultValue = "") String termo,
      @RequestParam(required = false) SolicitacaoPrivacidadeStatus status,
      @RequestParam(required = false) SolicitacaoPrivacidadeTipo tipo,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "12") int tamanho) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(solicitacaoService.listar(termo, status, tipo, pagina, tamanho));
  }

  @GetMapping("/{protocolo}")
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public ResponseEntity<SolicitacaoPrivacidadeResponse> detalhar(@PathVariable String protocolo) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(solicitacaoService.detalhar(protocolo));
  }

  @PatchMapping("/{protocolo}")
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public ResponseEntity<SolicitacaoPrivacidadeResponse> atualizar(
      @PathVariable String protocolo,
      @Valid @RequestBody SolicitacaoPrivacidadeUpdateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok()
        .cacheControl(CacheControl.noStore())
        .body(solicitacaoService.atualizar(protocolo, request, authentication.getName()));
  }
}
