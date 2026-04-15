package com.sistema_contabilidade.notificacao.controller;

import com.sistema_contabilidade.notificacao.dto.NotificacaoListResponse;
import com.sistema_contabilidade.notificacao.service.NotificacaoService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/notificacoes")
@RequiredArgsConstructor
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
}
