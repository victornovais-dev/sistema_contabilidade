package com.sistema_contabilidade.usuario.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

@DisplayName("UsuarioPadraoInitializer unit tests")
class UsuarioPadraoInitializerTest {

  @Test
  @DisplayName("Deve criar usuario padrao quando banco estiver vazio")
  void deveCriarUsuarioPadraoQuandoBancoEstiverVazio() {
    UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    RoleRepository roleRepository = mock(RoleRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);
    Role roleAdmin = new Role();
    roleAdmin.setNome("ADMIN");

    when(usuarioRepository.count()).thenReturn(0L);
    when(passwordEncoder.encode("123")).thenReturn("{scrypt}hash");
    when(roleRepository.findByNome("ADMIN")).thenReturn(Optional.of(roleAdmin));

    UsuarioPadraoInitializer initializer =
        new UsuarioPadraoInitializer(usuarioRepository, roleRepository, passwordEncoder);

    initializer.run();

    ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
    verify(usuarioRepository).save(captor.capture());
    Usuario usuarioSalvo = captor.getValue();
    assertEquals("Victor Novais", usuarioSalvo.getNome());
    assertEquals("victornovais77@gmail.com", usuarioSalvo.getEmail());
    assertEquals("{scrypt}hash", usuarioSalvo.getSenha());
    assertTrue(usuarioSalvo.getRoles().stream().anyMatch(role -> "ADMIN".equals(role.getNome())));
  }

  @Test
  @DisplayName("Nao deve criar usuario padrao quando banco ja tiver usuarios")
  void naoDeveCriarUsuarioPadraoQuandoBancoJaTiverUsuarios() {
    UsuarioRepository usuarioRepository = mock(UsuarioRepository.class);
    RoleRepository roleRepository = mock(RoleRepository.class);
    PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    when(usuarioRepository.count()).thenReturn(1L);

    UsuarioPadraoInitializer initializer =
        new UsuarioPadraoInitializer(usuarioRepository, roleRepository, passwordEncoder);

    initializer.run();

    verify(usuarioRepository, never()).save(org.mockito.ArgumentMatchers.any(Usuario.class));
    verify(roleRepository, never()).findByNome(org.mockito.ArgumentMatchers.anyString());
    verify(passwordEncoder, never()).encode(org.mockito.ArgumentMatchers.any());
  }
}
