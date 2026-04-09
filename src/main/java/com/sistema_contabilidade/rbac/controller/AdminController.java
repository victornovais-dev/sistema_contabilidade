package com.sistema_contabilidade.rbac.controller;

import com.sistema_contabilidade.rbac.dto.AssignPermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreatePermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreateRoleRequest;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.RoleService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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

  private static final String ADMIN_ROLE = "hasRole('ADMIN')";

  private final RoleService roleService;

  @org.springframework.web.bind.annotation.GetMapping("/roles")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<List<RoleDto>> listarRoles() {
    return ResponseEntity.ok(roleService.listarRoles());
  }

  @org.springframework.web.bind.annotation.GetMapping("/permissoes")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<List<PermissaoDto>> listarPermissoes() {
    return ResponseEntity.ok(roleService.listarPermissoes());
  }

  @PostMapping("/roles")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<RoleDto> criarRole(@Valid @RequestBody CreateRoleRequest request) {
    return ResponseEntity.ok(roleService.criarRole(request.nome()));
  }

  @PostMapping("/permissoes")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<PermissaoDto> criarPermissao(
      @Valid @RequestBody CreatePermissaoRequest request) {
    return ResponseEntity.ok(roleService.criarPermissao(request.nome()));
  }

  @PostMapping("/roles/{roleNome}/permissoes")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<RoleDto> adicionarPermissao(
      @PathVariable("roleNome") String roleNome,
      @Valid @RequestBody AssignPermissaoRequest request) {
    return ResponseEntity.ok(roleService.adicionarPermissaoNaRole(roleNome, request.permissao()));
  }

  @PostMapping("/usuarios/{usuarioId}/roles/{roleNome}")
  @PreAuthorize(ADMIN_ROLE)
  public ResponseEntity<UsuarioComRolesDto> atribuirRole(
      @PathVariable("usuarioId") UUID usuarioId, @PathVariable("roleNome") String roleNome) {
    return ResponseEntity.ok(roleService.atribuirRoleAoUsuario(usuarioId, roleNome));
  }
}
