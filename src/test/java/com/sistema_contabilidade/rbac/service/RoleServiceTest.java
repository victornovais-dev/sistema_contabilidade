package com.sistema_contabilidade.rbac.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.common.mapper.GenericModelMapperService;
import com.sistema_contabilidade.rbac.dto.PermissaoDto;
import com.sistema_contabilidade.rbac.dto.RoleDto;
import com.sistema_contabilidade.rbac.dto.RoleResumoDto;
import com.sistema_contabilidade.rbac.dto.UsuarioComRolesDto;
import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.PermissaoRepository;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService unit tests")
class RoleServiceTest {

  @Mock private RoleRepository roleRepository;

  @Mock private PermissaoRepository permissaoRepository;

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private GenericModelMapperService<Role, RoleDto> roleModelMapperService;
  @Mock private GenericModelMapperService<Permissao, PermissaoDto> permissaoModelMapperService;
  @Mock
  private GenericModelMapperService<Usuario, UsuarioComRolesDto> usuarioModelMapperService;

  @Test
  @DisplayName("Deve criar role")
  void deveCriarRole() {
    RoleService roleService = novoRoleService();

    // Arrange
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());
    Role role = new Role();
    role.setNome("ADMIN");
    when(roleRepository.save(any(Role.class))).thenReturn(role);
    RoleDto roleDto = new RoleDto(null, "ADMIN", Set.of());
    when(roleModelMapperService.convertToDto(role, RoleDto.class)).thenReturn(roleDto);

    // Act
    RoleDto resultado = roleService.criarRole("ADMIN");

    // Assert
    assertEquals("ADMIN", resultado.getNome());
  }

  @Test
  @DisplayName("Deve adicionar permissao na role")
  void deveAdicionarPermissaoNaRole() {
    RoleService roleService = novoRoleService();

    // Arrange
    Role role = new Role();
    role.setNome("ADMIN");
    Permissao permissao = new Permissao();
    permissao.setNome("USER_READ");
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(role));
    when(permissaoRepository.findByNome("USER_READ")).thenReturn(Optional.of(permissao));
    when(roleRepository.save(role)).thenReturn(role);
    RoleDto roleDto =
        new RoleDto(null, "ADMIN", new HashSet<>(Set.of(new PermissaoDto(null, "USER_READ"))));
    when(roleModelMapperService.convertToDto(role, RoleDto.class)).thenReturn(roleDto);

    // Act
    RoleDto resultado = roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ");

    // Assert
    assertEquals(1, resultado.getPermissoes().size());
    verify(roleRepository).save(role);
  }

  @Test
  @DisplayName("Deve atribuir role ao usuario")
  void deveAtribuirRoleAoUsuario() {
    RoleService roleService = novoRoleService();

    // Arrange
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = new Usuario();
    usuario.setId(usuarioId);
    usuario.setNome("Nome");
    usuario.setEmail("email@email.com");
    usuario.setRoles(new HashSet<>());
    Role role = new Role();
    role.setId(UUID.fromString("33333333-3333-3333-3333-333333333333"));
    role.setNome("USER");
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(roleRepository.findByNome("USER")).thenReturn(Optional.of(role));
    when(usuarioRepository.save(any(Usuario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    UsuarioComRolesDto usuarioComRolesDto =
        new UsuarioComRolesDto(
            usuarioId, "Nome", "email@email.com", Set.of(new RoleResumoDto(null, "USER")));
    when(usuarioModelMapperService.convertToDto(any(), any())).thenReturn(usuarioComRolesDto);

    // Act
    UsuarioComRolesDto resultado = roleService.atribuirRoleAoUsuario(usuarioId, "USER");

    // Assert
    assertEquals(1, resultado.getRoles().size());
  }

  private RoleService novoRoleService() {
    return new RoleService(
        roleRepository,
        permissaoRepository,
        usuarioRepository,
        roleModelMapperService,
        permissaoModelMapperService,
        usuarioModelMapperService);
  }
}
