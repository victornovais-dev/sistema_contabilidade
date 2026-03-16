package com.sistema_contabilidade.security.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@Slf4j
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  public static final String USER_DETAILS_CACHE = "userDetails";
  private static final long REDIS_WARN_THRESHOLD_MS = 20L;
  private final UsuarioRepository usuarioRepository;
  private final StringRedisTemplate redisTemplate;

  @Cacheable(value = USER_DETAILS_CACHE, key = "#p0")
  @Override
  public UserDetails loadUserByUsername(String username) {
    return carregarUserDetails(username);
  }

  @CachePut(value = USER_DETAILS_CACHE, key = "#p1")
  public UserDetails atualizarCacheUsuario(UUID usuarioId, String username) {
    removerAuthoritiesDoRedis(usuarioId);
    return carregarUserDetails(username);
  }

  @CacheEvict(value = USER_DETAILS_CACHE, key = "#p1")
  public void removerCacheUsuario(UUID usuarioId, String username) {
    removerAuthoritiesDoRedis(usuarioId);
  }

  @CacheEvict(value = USER_DETAILS_CACHE, allEntries = true)
  public void limparCacheUserDetails() {
    long startNanos = System.nanoTime();
    try {
      Set<String> keys = redisTemplate.keys("auth:user:*");
      if (keys != null && !keys.isEmpty()) {
        redisTemplate.delete(keys);
      }
      logIfSlow("keys/delete", startNanos);
    } catch (DataAccessException exception) {
      log.warn("Redis indisponivel ao limpar cache de authorities.", exception);
    }
  }

  private UserDetails carregarUserDetails(String username) {
    Usuario usuario =
        usuarioRepository
            .findByEmail(username)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));

    Collection<GrantedAuthority> authorities = authoritiesFromCacheOrDb(usuario);

    return new CachedUserDetails(usuario.getEmail(), usuario.getSenha(), authorities);
  }

  private void removerAuthoritiesDoRedis(UUID usuarioId) {
    long startNanos = System.nanoTime();
    try {
      redisTemplate.delete("auth:user:" + usuarioId);
      logIfSlow("delete", startNanos);
    } catch (DataAccessException exception) {
      log.warn("Redis indisponivel ao invalidar authorities por usuario.", exception);
    }
  }

  private Collection<GrantedAuthority> authoritiesFromCacheOrDb(Usuario usuario) {
    String key = "auth:user:" + usuario.getId();
    long readStartNanos = System.nanoTime();
    try {
      Set<String> cached = redisTemplate.opsForSet().members(key);
      logIfSlow("sMembers", readStartNanos);
      if (cached != null && !cached.isEmpty()) {
        return cached.stream()
            .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
            .toList();
      }
    } catch (DataAccessException exception) {
      log.warn("Redis indisponivel ao ler authorities. Fallback para banco.", exception);
    }

    List<GrantedAuthority> authorities = new ArrayList<>();
    usuario
        .getRoles()
        .forEach(
            role -> {
              authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getNome()));
              role.getPermissoes()
                  .forEach(
                      permissao ->
                          authorities.add(new SimpleGrantedAuthority(permissao.getNome())));
            });

    if (!authorities.isEmpty()) {
      long writeStartNanos = System.nanoTime();
      try {
        List<String> names = authorities.stream().map(GrantedAuthority::getAuthority).toList();
        redisTemplate.opsForSet().add(key, names.toArray(new String[0]));
        redisTemplate.expire(key, Duration.ofMinutes(30));
        logIfSlow("sAdd/expire", writeStartNanos);
      } catch (DataAccessException exception) {
        log.warn("Redis indisponivel ao gravar authorities. Seguindo sem cache.", exception);
      }
    }

    return authorities;
  }

  private void logIfSlow(String operation, long startNanos) {
    long elapsedMillis = Duration.ofNanos(System.nanoTime() - startNanos).toMillis();
    if (elapsedMillis > REDIS_WARN_THRESHOLD_MS) {
      log.warn("Redis lento em {}: {} ms", operation, elapsedMillis);
    }
  }

  private static final class CachedUserDetails implements UserDetails {

    private static final long serialVersionUID = 1L;
    private final String username;
    private final String password;
    private final List<GrantedAuthority> authorities;

    private CachedUserDetails(
        String username, String password, Collection<? extends GrantedAuthority> authorities) {
      this.username = username;
      this.password = password;
      this.authorities = List.copyOf(authorities);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
      return authorities;
    }

    @Override
    public String getPassword() {
      return password;
    }

    @Override
    public String getUsername() {
      return username;
    }

    @Override
    public boolean isAccountNonExpired() {
      return true;
    }

    @Override
    public boolean isAccountNonLocked() {
      return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
      return true;
    }

    @Override
    public boolean isEnabled() {
      return true;
    }
  }
}
