package com.sistema_contabilidade.security.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  public static final String USER_DETAILS_CACHE = "userDetails";
  private final UsuarioRepository usuarioRepository;

  @Cacheable(value = USER_DETAILS_CACHE, key = "#p0")
  @Override
  public UserDetails loadUserByUsername(String username) {
    Usuario usuario = buscarUsuarioPorEmail(username);
    Collection<GrantedAuthority> authorities = authoritiesFromDb(usuario);
    return new CachedUserDetails(usuario.getEmail(), usuario.getSenha(), authorities);
  }

  @Cacheable(value = USER_DETAILS_CACHE, key = "'id:' + #p0")
  public UserDetails loadUserById(UUID userId) {
    Usuario usuario = buscarUsuarioPorId(userId);
    Collection<GrantedAuthority> authorities = authoritiesFromDb(usuario);
    return new CachedUserDetails(usuario.getEmail(), usuario.getSenha(), authorities);
  }

  @CachePut(value = USER_DETAILS_CACHE, key = "#p1")
  public UserDetails atualizarCacheUsuario(UUID usuarioId, String username) {
    Usuario usuario = buscarUsuarioPorEmail(username);
    Collection<GrantedAuthority> authorities = authoritiesFromDb(usuario);
    return new CachedUserDetails(usuario.getEmail(), usuario.getSenha(), authorities);
  }

  @CacheEvict(value = USER_DETAILS_CACHE, key = "#p1")
  public void removerCacheUsuario(UUID usuarioId, String username) {
    // No-op intencional: apenas invalida a entrada do cache Caffeine via @CacheEvict.
  }

  @CacheEvict(value = USER_DETAILS_CACHE, allEntries = true)
  public void limparCacheUserDetails() {
    // No-op intencional: a limpeza total eh feita pelo proxy de cache via @CacheEvict.
  }

  private Usuario buscarUsuarioPorEmail(String username) {
    return usuarioRepository
        .findByEmail(username)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }

  private Usuario buscarUsuarioPorId(UUID userId) {
    return usuarioRepository
        .findWithRolesById(userId)
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }

  private Collection<GrantedAuthority> authoritiesFromDb(Usuario usuario) {
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
    return authorities;
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
