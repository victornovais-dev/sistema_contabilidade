package com.sistema_contabilidade.rbac.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.model.RoleNivel;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

@DisplayName("RoleNivelInitializer unit tests")
class RoleNivelInitializerTest {

  @Test
  @DisplayName("Deve criar roles padrao quando nao existem")
  void deveCriarRolesPadraoQuandoNaoExistem() {
    RoleRepository roleRepository = mock(RoleRepository.class);
    UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    when(roleRepository.findByNome(any())).thenReturn(Optional.empty());
    when(usuarioRepository.findAll()).thenReturn(List.of());
    RoleNivelInitializer initializer = new RoleNivelInitializer(roleRepository, usuarioRepository);

    initializer.run();

    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository, times(RoleNivel.values().length)).save(captor.capture());
    List<String> nomes = captor.getAllValues().stream().map(Role::getNome).toList();
    for (RoleNivel nivel : RoleNivel.values()) {
      assertTrue(nomes.contains(nivel.valorBanco()));
    }
    verify(roleRepository, never()).delete(any(Role.class));
  }

  @Test
  @DisplayName("Nao deve salvar role padrao que ja existe")
  void naoDeveSalvarRolePadraoQueJaExiste() {
    RoleRepository roleRepository = mock(RoleRepository.class);
    UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    when(usuarioRepository.findAll()).thenReturn(List.of());
    when(roleRepository.findByNome(any()))
        .thenAnswer(
            invocation -> {
              String nome = invocation.getArgument(0);
              if ("ADMIN".equals(nome)) {
                Role role = new Role();
                role.setNome("ADMIN");
                return Optional.of(role);
              }
              return Optional.empty();
            });
    RoleNivelInitializer initializer = new RoleNivelInitializer(roleRepository, usuarioRepository);

    initializer.run();

    int totalEsperado = RoleNivel.values().length - 1;
    ArgumentCaptor<Role> captor = ArgumentCaptor.forClass(Role.class);
    verify(roleRepository, times(totalEsperado)).save(captor.capture());
    List<String> nomes = captor.getAllValues().stream().map(Role::getNome).toList();
    assertTrue(nomes.stream().noneMatch("ADMIN"::equals));
    assertEquals(totalEsperado, nomes.size());
  }

  @Test
  @DisplayName("Deve remover roles descontinuadas e desvincular dos usuarios")
  void deveRemoverRolesDescontinuadasEDesvincularDosUsuarios() {
    RoleRepository roleRepository = mock(RoleRepository.class);
    UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);

    Role kim = new Role();
    kim.setNome("KIM KATAGUIRI");
    Role valdemar = new Role();
    valdemar.setNome("VALDEMAR");
    Role admin = new Role();
    admin.setNome("ADMIN");

    Usuario usuario = new Usuario();
    usuario.getRoles().add(kim);
    usuario.getRoles().add(valdemar);
    usuario.getRoles().add(admin);

    when(roleRepository.findByNome("KIM KATAGUIRI")).thenReturn(Optional.of(kim));
    when(roleRepository.findByNome("VALDEMAR")).thenReturn(Optional.of(valdemar));
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(admin));
    when(roleRepository.findByNome("MANAGER")).thenReturn(Optional.empty());
    when(roleRepository.findByNome("TARCISIO")).thenReturn(Optional.empty());
    when(usuarioRepository.findAll()).thenReturn(List.of(usuario));

    RoleNivelInitializer initializer = new RoleNivelInitializer(roleRepository, usuarioRepository);

    initializer.run();

    assertFalse(
        usuario.getRoles().stream().anyMatch(role -> "KIM KATAGUIRI".equals(role.getNome())));
    assertFalse(usuario.getRoles().stream().anyMatch(role -> "VALDEMAR".equals(role.getNome())));
    assertTrue(usuario.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getNome())));
    verify(roleRepository).delete(kim);
    verify(roleRepository).delete(valdemar);
  }
}
