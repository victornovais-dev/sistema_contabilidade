package com.sistema_contabilidade.duvida.controller;

import com.sistema_contabilidade.duvida.dto.DuvidaCreateRequest;
import com.sistema_contabilidade.duvida.dto.DuvidaCreateResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaListResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaResponse;
import com.sistema_contabilidade.duvida.dto.DuvidaStatusUpdateRequest;
import com.sistema_contabilidade.duvida.model.DuvidaStatus;
import com.sistema_contabilidade.duvida.service.DuvidaService;
import com.sistema_contabilidade.security.util.SecurityPaths;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityPaths.PUBLIC_QUESTIONS_API_BASE)
@RequiredArgsConstructor
public class DuvidaController {

  private static final String ADMIN_ROLE_EXPRESSION = "hasRole('ADMIN')";
  private final DuvidaService duvidaService;

  @PostMapping
  public ResponseEntity<DuvidaCreateResponse> registrar(
      @Valid @RequestBody DuvidaCreateRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(duvidaService.registrar(request));
  }

  @GetMapping
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public DuvidaListResponse listar(
      @RequestParam(defaultValue = "") String termo,
      @RequestParam(required = false) DuvidaStatus status,
      @RequestParam(defaultValue = "0") int pagina,
      @RequestParam(defaultValue = "12") int tamanho) {
    return duvidaService.listar(termo, status, pagina, tamanho);
  }

  @PatchMapping("/{protocolo}/status")
  @PreAuthorize(ADMIN_ROLE_EXPRESSION)
  public DuvidaResponse atualizarStatus(
      @PathVariable UUID protocolo, @Valid @RequestBody DuvidaStatusUpdateRequest request) {
    return duvidaService.atualizarStatus(protocolo, request.status());
  }
}
