package com.sistema_contabilidade.item.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("ItemAccessUtils unit tests")
class ItemAccessUtilsTest {

  @Test
  @DisplayName("Deve reconhecer admin pela authority")
  void deveReconhecerAdminPelaAuthority() {
    assertTrue(ItemAccessUtils.isAdmin(auth("admin@email.com", "ROLE_ADMIN")));
    assertFalse(ItemAccessUtils.isAdmin(auth("user@email.com", "ROLE_OPERADOR")));
  }

  @Test
  @DisplayName("Deve buscar usuario autenticado no repositorio")
  void deveBuscarUsuarioAutenticadoNoRepositorio() {
    UsuarioRepository usuarioRepository = Mockito.mock(UsuarioRepository.class);
    Usuario usuario = usuarioComRoles("user@email.com", "financeiro", "operador");
    when(usuarioRepository.findByEmail("user@email.com")).thenReturn(Optional.of(usuario));

    Usuario encontrado =
        ItemAccessUtils.buscarUsuarioAutenticado(
            auth("user@email.com", "ROLE_FINANCEIRO"), usuarioRepository);

    assertEquals("user@email.com", encontrado.getEmail());
    assertEquals(Set.of("FINANCEIRO", "OPERADOR"), ItemAccessUtils.extrairRoleNomes(encontrado));
  }

  @Test
  @DisplayName("Deve rejeitar filtro por role nao pertencente ao usuario")
  void deveRejeitarFiltroPorRoleNaoPertencenteAoUsuario() {
    Set<String> roleNomes = Set.of("OPERADOR");

    ResponseStatusException ex =
        assertThrows(
            ResponseStatusException.class,
            () -> ItemAccessUtils.validarRoleFiltro("ADMIN", roleNomes));

    assertEquals(403, ex.getStatusCode().value());
  }

  private UsernamePasswordAuthenticationToken auth(String email, String... authorities) {
    return new UsernamePasswordAuthenticationToken(
        email,
        "n/a",
        java.util.Arrays.stream(authorities).map(SimpleGrantedAuthority::new).toList());
  }

  private Usuario usuarioComRoles(String email, String... roleNomes) {
    Usuario usuario = new Usuario();
    usuario.setEmail(email);
    usuario.setNome(email);
    usuario.setSenha("123456");
    for (String roleNome : roleNomes) {
      Role role = new Role();
      role.setNome(roleNome);
      usuario.getRoles().add(role);
    }
    return usuario;
  }
}
