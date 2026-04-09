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
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminController tests")
class AdminControllerWebMvcTest {

  @Mock private RoleService roleService;

  @InjectMocks private AdminController adminController;

  @Test
  @DisplayName("Deve listar roles")
  void listarRolesDeveRetornarOk() {
    RoleDto roleDto = new RoleDto(UUID.randomUUID(), "ADMIN", Set.of());
    when(roleService.listarRoles()).thenReturn(List.of(roleDto));

    var response = adminController.listarRoles();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    assertEquals("ADMIN", response.getBody().getFirst().getNome());
    verify(roleService).listarRoles();
  }

  @Test
  @DisplayName("Deve listar permissoes")
  void listarPermissoesDeveRetornarOk() {
    PermissaoDto permissaoDto = new PermissaoDto(UUID.randomUUID(), "USER_READ");
    when(roleService.listarPermissoes()).thenReturn(List.of(permissaoDto));

    var response = adminController.listarPermissoes();

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals(1, response.getBody().size());
    assertEquals("USER_READ", response.getBody().getFirst().getNome());
    verify(roleService).listarPermissoes();
  }

  @Test
  @DisplayName("Deve criar role")
  void criarRoleDeveRetornarOk() {
    CreateRoleRequest request = new CreateRoleRequest("ADMIN");
    RoleDto roleDto = new RoleDto(UUID.randomUUID(), "ADMIN", Set.of());
    when(roleService.criarRole("ADMIN")).thenReturn(roleDto);

    var response = adminController.criarRole(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("ADMIN", response.getBody().getNome());
    verify(roleService).criarRole("ADMIN");
  }

  @Test
  @DisplayName("Deve criar permissao")
  void criarPermissaoDeveRetornarOk() {
    CreatePermissaoRequest request = new CreatePermissaoRequest("USER_READ");
    PermissaoDto permissaoDto = new PermissaoDto(UUID.randomUUID(), "USER_READ");
    when(roleService.criarPermissao("USER_READ")).thenReturn(permissaoDto);

    var response = adminController.criarPermissao(request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("USER_READ", response.getBody().getNome());
    verify(roleService).criarPermissao("USER_READ");
  }

  @Test
  @DisplayName("Deve adicionar permissao na role")
  void adicionarPermissaoDeveRetornarOk() {
    AssignPermissaoRequest request = new AssignPermissaoRequest("USER_READ");
    RoleDto roleDto =
        new RoleDto(
            UUID.randomUUID(), "ADMIN", Set.of(new PermissaoDto(UUID.randomUUID(), "USER_READ")));
    when(roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ")).thenReturn(roleDto);

    var response = adminController.adicionarPermissao("ADMIN", request);

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("ADMIN", response.getBody().getNome());
    assertEquals("USER_READ", response.getBody().getPermissoes().iterator().next().getNome());
    verify(roleService).adicionarPermissaoNaRole("ADMIN", "USER_READ");
  }

  @Test
  @DisplayName("Deve atribuir role ao usuario")
  void atribuirRoleDeveRetornarOk() {
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UsuarioComRolesDto usuario =
        new UsuarioComRolesDto(
            usuarioId,
            "Ana",
            "ana@email.com",
            Set.of(new RoleResumoDto(UUID.randomUUID(), "ADMIN")));
    when(roleService.atribuirRoleAoUsuario(usuarioId, "ADMIN")).thenReturn(usuario);

    var response = adminController.atribuirRole(usuarioId, "ADMIN");

    assertEquals(HttpStatus.OK, response.getStatusCode());
    assertEquals("ana@email.com", response.getBody().getEmail());
    verify(roleService).atribuirRoleAoUsuario(usuarioId, "ADMIN");
  }
}
