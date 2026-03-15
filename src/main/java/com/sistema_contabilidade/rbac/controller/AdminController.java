package com.sistema_contabilidade.rbac.controller;

import com.sistema_contabilidade.rbac.dto.AssignPermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreatePermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreateRoleRequest;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.RoleService;
import jakarta.validation.Valid;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin")
@Validated
@RequiredArgsConstructor
public class AdminController {

  private final RoleService roleService;

  @PostMapping("/roles")
  public ResponseEntity<RoleDto> criarRole(@Valid @RequestBody CreateRoleRequest request) {
    return ResponseEntity.ok(roleService.criarRole(request.nome()));
  }

  @PostMapping("/permissoes")
  public ResponseEntity<PermissaoDto> criarPermissao(
      @Valid @RequestBody CreatePermissaoRequest request) {
    return ResponseEntity.ok(roleService.criarPermissao(request.nome()));
  }

  @PostMapping("/roles/{roleNome}/permissoes")
  public ResponseEntity<RoleDto> adicionarPermissao(
      @PathVariable String roleNome, @Valid @RequestBody AssignPermissaoRequest request) {
    return ResponseEntity.ok(roleService.adicionarPermissaoNaRole(roleNome, request.permissao()));
  }

  @PostMapping("/usuarios/{usuarioId}/roles/{roleNome}")
  public ResponseEntity<UsuarioComRolesDto> atribuirRole(
      @PathVariable UUID usuarioId, @PathVariable String roleNome) {
    return ResponseEntity.ok(roleService.atribuirRoleAoUsuario(usuarioId, roleNome));
  }
}
