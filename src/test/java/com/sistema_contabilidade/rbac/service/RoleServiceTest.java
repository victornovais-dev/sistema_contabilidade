package com.sistema_contabilidade.rbac.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.common.mapper.RbacMapper;
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
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("RoleService unit tests")
class RoleServiceTest {

  @Mock private RoleRepository roleRepository;

  @Mock private PermissaoRepository permissaoRepository;

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private RbacMapper rbacMapper;

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
    when(rbacMapper.toRoleDto(role)).thenReturn(roleDto);

    // Act
    RoleDto resultado = roleService.criarRole("ADMIN");

    // Assert
    assertEquals("ADMIN", resultado.getNome());
  }

  @Test
  @DisplayName("Deve retornar conflito ao criar role existente")
  void deveRetornarConflitoAoCriarRoleExistente() {
    RoleService roleService = novoRoleService();
    Role existente = new Role();
    existente.setNome("ADMIN");
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(existente));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> roleService.criarRole("ADMIN"));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve criar permissao")
  void deveCriarPermissao() {
    RoleService roleService = novoRoleService();

    when(permissaoRepository.findByNome("USER_READ")).thenReturn(Optional.empty());
    Permissao permissao = new Permissao();
    permissao.setNome("USER_READ");
    when(permissaoRepository.save(any(Permissao.class))).thenReturn(permissao);
    PermissaoDto permissaoDto = new PermissaoDto(UUID.randomUUID(), "USER_READ");
    when(rbacMapper.toPermissaoDto(permissao)).thenReturn(permissaoDto);

    PermissaoDto resultado = roleService.criarPermissao("USER_READ");

    assertEquals("USER_READ", resultado.getNome());
  }

  @Test
  @DisplayName("Deve retornar conflito ao criar permissao existente")
  void deveRetornarConflitoAoCriarPermissaoExistente() {
    RoleService roleService = novoRoleService();
    Permissao existente = new Permissao();
    existente.setNome("USER_READ");
    when(permissaoRepository.findByNome("USER_READ")).thenReturn(Optional.of(existente));

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> roleService.criarPermissao("USER_READ"));

    assertEquals(HttpStatus.CONFLICT, ex.getStatusCode());
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
    when(rbacMapper.toRoleDto(role)).thenReturn(roleDto);

    // Act
    RoleDto resultado = roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ");

    // Assert
    assertEquals(1, resultado.getPermissoes().size());
    verify(roleRepository).save(role);
  }

  @Test
  @DisplayName("Deve retornar not found ao adicionar permissao em role inexistente")
  void deveRetornarNotFoundAoAdicionarPermissaoEmRoleInexistente() {
    RoleService roleService = novoRoleService();
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve retornar not found ao adicionar permissao inexistente")
  void deveRetornarNotFoundAoAdicionarPermissaoInexistente() {
    RoleService roleService = novoRoleService();
    Role role = new Role();
    role.setNome("ADMIN");
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(role));
    when(permissaoRepository.findByNome("USER_READ")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> roleService.adicionarPermissaoNaRole("ADMIN", "USER_READ"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
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
    role.setNome("CUSTOMER");
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(roleRepository.findByNome("CUSTOMER")).thenReturn(Optional.of(role));
    when(usuarioRepository.save(any(Usuario.class)))
        .thenAnswer(invocation -> invocation.getArgument(0));
    UsuarioComRolesDto usuarioComRolesDto =
        new UsuarioComRolesDto(
            usuarioId, "Nome", "email@email.com", Set.of(new RoleResumoDto(null, "CUSTOMER")));
    when(rbacMapper.toUsuarioComRolesDto(any())).thenReturn(usuarioComRolesDto);

    // Act
    UsuarioComRolesDto resultado = roleService.atribuirRoleAoUsuario(usuarioId, "CUSTOMER");

    // Assert
    assertEquals(1, resultado.getRoles().size());
  }

  @Test
  @DisplayName("Deve retornar not found ao atribuir role para usuario inexistente")
  void deveRetornarNotFoundAoAtribuirRoleParaUsuarioInexistente() {
    RoleService roleService = novoRoleService();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> roleService.atribuirRoleAoUsuario(usuarioId, "CUSTOMER"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve retornar not found ao atribuir role inexistente")
  void deveRetornarNotFoundAoAtribuirRoleInexistente() {
    RoleService roleService = novoRoleService();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    Usuario usuario = new Usuario();
    usuario.setId(usuarioId);
    usuario.setRoles(new HashSet<>());
    when(usuarioRepository.findById(usuarioId)).thenReturn(Optional.of(usuario));
    when(roleRepository.findByNome("CUSTOMER")).thenReturn(Optional.empty());

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> roleService.atribuirRoleAoUsuario(usuarioId, "CUSTOMER"));

    assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
  }

  @Test
  @DisplayName("Deve retornar 400 quando role for invalida")
  void deveRetornarBadRequestQuandoRoleInvalida() {
    RoleService roleService = novoRoleService();

    ResponseStatusException ex =
        assertThrows(ResponseStatusException.class, () -> roleService.criarRole("USER"));

    assertEquals(HttpStatus.BAD_REQUEST, ex.getStatusCode());
  }

  private RoleService novoRoleService() {
    return new RoleService(
        roleRepository, permissaoRepository, usuarioRepository, rbacMapper);
  }
}
