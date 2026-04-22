package com.sistema_contabilidade.notificacao.controller;

import com.sistema_contabilidade.notificacao.dto.NotificacaoLimpezaUpdateRequest;
import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTABIL')")
public class NotificacaoController {

  private final NotificacaoService notificacaoService;

  @GetMapping
  public ResponseEntity<List<NotificacaoListResponse>> listar(
      Authentication authentication, @RequestParam(name = "role", required = false) String role) {
    return ResponseEntity.ok(notificacaoService.listar(authentication, role));
  }

  @GetMapping("/roles")
  public ResponseEntity<List<String>> listarRolesDisponiveis(Authentication authentication) {
    return ResponseEntity.ok(notificacaoService.listarRolesDisponiveis(authentication));
  }

  @PatchMapping("/{id}/limpeza")
  public ResponseEntity<NotificacaoListResponse> atualizarLimpeza(
      Authentication authentication,
      @PathVariable("id") UUID id,
      @Valid @RequestBody NotificacaoLimpezaUpdateRequest request) {
    return ResponseEntity.ok(
        notificacaoService.atualizarLimpeza(
            authentication, id, Boolean.TRUE.equals(request.limpa())));
  }
}
