package com.sistema_contabilidade.rbac.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RBAC DTOs unit tests")
class RbacDtosTest {

  @Test
  @DisplayName("Deve expor campos de PermissaoDto e RoleDto")
  void deveExporCamposDePermissaoERoleDto() {
    UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
    PermissaoDto permissaoDto = new PermissaoDto(id, "PERMISSAO_TESTE");
    RoleDto roleDto = new RoleDto(id, "ADMIN", Set.of(permissaoDto));

    assertEquals(id, permissaoDto.getId());
    assertEquals("PERMISSAO_TESTE", permissaoDto.getNome());
    assertEquals(id, roleDto.getId());
    assertEquals("ADMIN", roleDto.getNome());
    assertNotNull(roleDto.getPermissoes());
  }

  @Test
  @DisplayName("Deve expor campos de RoleResumoDto e UsuarioComRolesDto")
  void deveExporCamposDeRoleResumoEUsuarioComRolesDto() {
    UUID id = UUID.fromString("22222222-2222-2222-2222-222222222222");
    RoleResumoDto roleResumoDto = new RoleResumoDto(id, "OPERATOR");
    UsuarioComRolesDto usuario =
        new UsuarioComRolesDto(id, "Usuario", "user@email.com", Set.of(roleResumoDto));

    assertEquals(id, roleResumoDto.getId());
    assertEquals("OPERATOR", roleResumoDto.getNome());
    assertEquals(id, usuario.getId());
    assertEquals("Usuario", usuario.getNome());
    assertEquals("user@email.com", usuario.getEmail());
    assertNotNull(usuario.getRoles());
  }
}
