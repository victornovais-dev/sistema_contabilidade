package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService unit tests")
class CustomUserDetailsServiceTest {

  @Mock private UsuarioRepository usuarioRepository;

  @Test
  @DisplayName("Deve lancar 401 quando usuario nao existe")
  void deveLancarUnauthorizedQuandoUsuarioNaoExiste() {
    when(usuarioRepository.findByEmail("invalido@email.com")).thenReturn(Optional.empty());
    CustomUserDetailsService service = new CustomUserDetailsService(usuarioRepository);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> service.loadUserByUsername("invalido@email.com"));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  @Test
  @DisplayName("Deve montar authorities do banco no login")
  void deveMontarAuthoritiesDoBancoNoLogin() {
    Usuario usuario = criarUsuarioComRolePermissao();
    when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    CustomUserDetailsService service = new CustomUserDetailsService(usuarioRepository);

    UserDetails userDetails = service.loadUserByUsername(usuario.getEmail());

    assertEquals(usuario.getEmail(), userDetails.getUsername());
    assertEquals(2, userDetails.getAuthorities().size());
    assertTrue(
        userDetails.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    assertTrue(!(userDetails instanceof CredentialsContainer));
  }

  @Test
  @DisplayName("Deve remover cache sem falhar")
  void deveRemoverCacheSemFalhar() {
    CustomUserDetailsService service = new CustomUserDetailsService(usuarioRepository);
    assertDoesNotThrow(
        () ->
            service.removerCacheUsuario(
                UUID.fromString("11111111-1111-1111-1111-111111111111"), "ana@email.com"));
  }

  private Usuario criarUsuarioComRolePermissao() {
    Permissao permissao = new Permissao();
    permissao.setNome("usuarios:read");

    Role role = new Role();
    role.setNome("ADMIN");
    role.getPermissoes().add(permissao);

    Usuario usuario = new Usuario();
    usuario.setId(UUID.fromString("11111111-1111-1111-1111-111111111111"));
    usuario.setNome("Ana");
    usuario.setEmail("ana@email.com");
    usuario.setSenha("hash");
    usuario.getRoles().add(role);
    return usuario;
  }
}
