package com.sistema_contabilidade.rbac.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.dto.AssignPermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreatePermissaoRequest;
import com.sistema_contabilidade.rbac.dto.CreateRoleRequest;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.service.RoleService;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController unit tests")
class AdminControllerTest {

  @Mock private RoleService roleService;
  @InjectMocks private AdminController adminController;

  @Test
  @DisplayName("Deve criar role delegando para service")
  void criarRoleDeveDelegarParaService() {
    CreateRoleRequest request = new CreateRoleRequest("ADMIN");
    RoleDto roleDto = new RoleDto(UUID.randomUUID(), "ADMIN", Set.of());
    when(roleService.criarRole("ADMIN")).thenReturn(roleDto);

    ResponseEntity<RoleDto> response = adminController.criarRole(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("ADMIN", response.getBody().getNome());
    verify(roleService).criarRole("ADMIN");
  }

  @Test
  @DisplayName("Deve criar permissao delegando para service")
  void criarPermissaoDeveDelegarParaService() {
    CreatePermissaoRequest request = new CreatePermissaoRequest("USER_READ");
    PermissaoDto permissaoDto = new PermissaoDto(UUID.randomUUID(), "USER_READ");
    when(roleService.criarPermissao("USER_READ")).thenReturn(permissaoDto);

    ResponseEntity<PermissaoDto> response = adminController.criarPermissao(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("USER_READ", response.getBody().getNome());
    verify(roleService).criarPermissao("USER_READ");
  }

  @Test
  @DisplayName("Deve adicionar permissao na role delegando para service")
  void adicionarPermissaoDeveDelegarParaService() {
    AssignPermissaoRequest request = new AssignPermissaoRequest("USER_READ");
    RoleDto roleDto =
        new RoleDto(
            UUID.randomUUID(), "ADMIN", Set.of(new PermissaoDto(UUID.randomUUID(), "USER_READ")));
    when(roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ")).thenReturn(roleDto);

    ResponseEntity<RoleDto> response = adminController.adicionarPermissao("ADMIN", request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getPermissoes().size());
    verify(roleService).adicionarPermissaoNaRole("ADMIN", "USER_READ");
  }

  @Test
  @DisplayName("Deve atribuir role ao usuario delegando para service")
  void atribuirRoleDeveDelegarParaService() {
    UUID usuarioId = UUID.randomUUID();
    UsuarioComRolesDto usuarioDto =
        new UsuarioComRolesDto(
            usuarioId,
            "Ana",
            "ana@email.com",
            Set.of(new RoleResumoDto(UUID.randomUUID(), "USER")));
    when(roleService.atribuirRoleAoUsuario(usuarioId, "USER")).thenReturn(usuarioDto);

    ResponseEntity<UsuarioComRolesDto> response = adminController.atribuirRole(usuarioId, "USER");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().getRoles().size());
    verify(roleService).atribuirRoleAoUsuario(usuarioId, "USER");
  }
}
