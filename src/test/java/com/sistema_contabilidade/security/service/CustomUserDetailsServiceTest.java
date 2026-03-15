package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.rbac.model.Permissao;
import com.sistema_contabilidade.rbac.model.Role;
import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.Duration;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomUserDetailsService unit tests")
class CustomUserDetailsServiceTest {

  @Mock private UsuarioRepository usuarioRepository;
  @Mock private StringRedisTemplate redisTemplate;
  @Mock private SetOperations<String, String> setOperations;

  @Test
  @DisplayName("Deve lançar 401 quando usuário não existe")
  void deveLancarUnauthorizedQuandoUsuarioNaoExiste() {
    when(usuarioRepository.findByEmail("invalido@email.com")).thenReturn(Optional.empty());
    CustomUserDetailsService service =
        new CustomUserDetailsService(usuarioRepository, redisTemplate);

    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class, () -> service.loadUserByUsername("invalido@email.com"));

    assertEquals(HttpStatus.UNAUTHORIZED, exception.getStatusCode());
  }

  @Test
  @DisplayName("Deve usar autoridades do cache quando disponíveis")
  void deveUsarAutoridadesDoCacheQuandoDisponiveis() {
    Usuario usuario = criarUsuarioComRolePermissao();
    String key = "auth:user:" + usuario.getId();
    when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(key)).thenReturn(Set.of("ROLE_ADMIN", "usuarios:read"));
    CustomUserDetailsService service =
        new CustomUserDetailsService(usuarioRepository, redisTemplate);

    UserDetails userDetails = service.loadUserByUsername(usuario.getEmail());

    assertEquals(usuario.getEmail(), userDetails.getUsername());
    assertEquals(2, userDetails.getAuthorities().size());
    assertTrue(
        userDetails.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    verify(setOperations, never()).add(eq(key), any(String[].class));
    verify(redisTemplate, never()).expire(eq(key), any(Duration.class));
  }

  @Test
  @DisplayName("Deve montar autoridades do banco e salvar no cache")
  void deveMontarAutoridadesDoBancoESalvarNoCache() {
    Usuario usuario = criarUsuarioComRolePermissao();
    String key = "auth:user:" + usuario.getId();
    when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(key)).thenReturn(Set.of());
    CustomUserDetailsService service =
        new CustomUserDetailsService(usuarioRepository, redisTemplate);

    UserDetails userDetails = service.loadUserByUsername(usuario.getEmail());

    assertEquals(2, userDetails.getAuthorities().size());
    assertTrue(
        userDetails.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("ROLE_ADMIN")));
    assertTrue(
        userDetails.getAuthorities().stream()
            .anyMatch(authority -> authority.getAuthority().equals("usuarios:read")));
    verify(setOperations).add(key, "ROLE_ADMIN", "usuarios:read");
    verify(redisTemplate).expire(key, Duration.ofMinutes(30));
  }

  @Test
  @DisplayName("Deve fazer fallback para banco quando Redis estiver indisponivel")
  void deveFazerFallbackParaBancoQuandoRedisIndisponivel() {
    Usuario usuario = criarUsuarioComRolePermissao();
    String key = "auth:user:" + usuario.getId();
    when(usuarioRepository.findByEmail(usuario.getEmail())).thenReturn(Optional.of(usuario));
    when(redisTemplate.opsForSet()).thenReturn(setOperations);
    when(setOperations.members(key))
        .thenThrow(new RedisConnectionFailureException("redis indisponivel"));
    when(setOperations.add(eq(key), any(String[].class)))
        .thenThrow(new RedisConnectionFailureException("redis indisponivel"));
    CustomUserDetailsService service =
        new CustomUserDetailsService(usuarioRepository, redisTemplate);

    UserDetails userDetails = service.loadUserByUsername(usuario.getEmail());

    assertEquals(usuario.getEmail(), userDetails.getUsername());
    assertEquals(2, userDetails.getAuthorities().size());
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
