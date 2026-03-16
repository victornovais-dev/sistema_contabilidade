package com.sistema_contabilidade.common.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

@DisplayName("RbacMapper unit tests")
class RbacMapperTest {

  private final RbacMapper mapper = Mappers.getMapper(RbacMapper.class);

  @Test
  @DisplayName("Deve mapear Role para RoleDto com permissoes")
  void deveMapearRoleParaRoleDtoComPermissoes() {
    Permissao permissao = new Permissao();
    permissao.setNome("USER_READ");
    Role role = new Role();
    role.setNome("ADMIN");
    role.setPermissoes(Set.of(permissao));

    RoleDto roleDto = mapper.toRoleDto(role);

    assertEquals("ADMIN", roleDto.getNome());
    assertNotNull(roleDto.getPermissoes());
    assertEquals(1, roleDto.getPermissoes().size());
    assertEquals("USER_READ", roleDto.getPermissoes().iterator().next().getNome());
  }

  @Test
  @DisplayName("Deve mapear Usuario para UsuarioComRolesDto")
  void deveMapearUsuarioParaUsuarioComRolesDto() {
    Role role = new Role();
    role.setNome("SUPPORT");
    Usuario usuario = new Usuario();
    usuario.setNome("Carlos");
    usuario.setEmail("carlos@email.com");
    usuario.setRoles(Set.of(role));

    UsuarioComRolesDto usuarioDto = mapper.toUsuarioComRolesDto(usuario);

    assertEquals("Carlos", usuarioDto.getNome());
    assertEquals("carlos@email.com", usuarioDto.getEmail());
    assertNotNull(usuarioDto.getRoles());
    assertEquals(1, usuarioDto.getRoles().size());
    assertEquals("SUPPORT", usuarioDto.getRoles().iterator().next().getNome());
  }
}
