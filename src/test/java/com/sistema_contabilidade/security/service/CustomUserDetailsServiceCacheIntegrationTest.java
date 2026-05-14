package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@DisplayName("CustomUserDetailsService cache integration tests")
class CustomUserDetailsServiceCacheIntegrationTest {

  @Autowired private CustomUserDetailsService customUserDetailsService;
  @Autowired private CacheManager cacheManager;

  @MockitoBean private UsuarioRepository usuarioRepository;

  @BeforeEach
  void limparCache() {
    Cache cache = cacheManager.getCache(CustomUserDetailsService.USER_DETAILS_CACHE);
    if (cache != null) {
      cache.clear();
    }
  }

  @Test
  @DisplayName("Deve aquecer cache por email no bean proxied")
  void deveAquecerCachePorEmailNoBeanProxied() {
    Usuario usuario = criarUsuarioComRolePermissao();

    UserDetails userDetails = customUserDetailsService.aquecerCacheUsuario(usuario);
    Cache.ValueWrapper cacheValue =
        cacheManager.getCache(CustomUserDetailsService.USER_DETAILS_CACHE).get(usuario.getEmail());

    assertEquals(usuario.getEmail(), userDetails.getUsername());
    assertNotNull(cacheValue);
    assertNotNull(cacheValue.get());
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
