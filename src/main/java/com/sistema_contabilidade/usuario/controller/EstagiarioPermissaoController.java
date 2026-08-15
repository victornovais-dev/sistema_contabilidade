package com.sistema_contabilidade.usuario.controller;

import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.security.util.SecurityPaths;
import com.sistema_contabilidade.usuario.dto.EstagiarioPermissoesUpdateRequest;
import com.sistema_contabilidade.usuario.service.EstagiarioPermissaoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(SecurityPaths.INTERNS_API_BASE)
@Validated
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ADMIN','CONTABIL')")
public class EstagiarioPermissaoController {

  private final EstagiarioPermissaoService estagiarioPermissaoService;

  @GetMapping("/roles")
  public ResponseEntity<Set<String>> listarRolesDeCampanhaDisponiveis(
      Authentication authentication) {
    return ResponseEntity.ok(
        estagiarioPermissaoService.listarRolesDeCampanhaDisponiveis(authentication));
  }

  @GetMapping("/por-email")
  public ResponseEntity<UsuarioComRolesDto> buscarPorEmail(
      @RequestParam("email") @NotBlank @Email String email) {
    return ResponseEntity.ok(estagiarioPermissaoService.buscarPorEmail(email));
  }

  @PutMapping("/por-email")
  public ResponseEntity<UsuarioComRolesDto> atualizarPermissoes(
      @Valid @RequestBody EstagiarioPermissoesUpdateRequest request,
      Authentication authentication) {
    return ResponseEntity.ok(
        estagiarioPermissaoService.atualizarPermissoes(request, authentication));
  }
}
