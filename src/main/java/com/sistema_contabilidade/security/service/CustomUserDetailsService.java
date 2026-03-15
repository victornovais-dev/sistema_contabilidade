package com.sistema_contabilidade.security.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

  private final UsuarioRepository usuarioRepository;
  private final StringRedisTemplate redisTemplate;

  @Override
  public UserDetails loadUserByUsername(String username) {
    Usuario usuario =
        usuarioRepository
            .findByEmail(username)
            .orElseThrow(
                () ->
                    new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));

    Collection<GrantedAuthority> authorities = authoritiesFromCacheOrDb(usuario);

    return User.withUsername(usuario.getEmail())
        .password(usuario.getSenha())
        .authorities(authorities)
        .build();
  }

  private Collection<GrantedAuthority> authoritiesFromCacheOrDb(Usuario usuario) {
    String key = "auth:user:" + usuario.getId();
    Set<String> cached = redisTemplate.opsForSet().members(key);
    if (cached != null && !cached.isEmpty()) {
      return cached.stream()
          .map(authority -> (GrantedAuthority) new SimpleGrantedAuthority(authority))
          .toList();
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
      List<String> names = authorities.stream().map(GrantedAuthority::getAuthority).toList();
      redisTemplate.opsForSet().add(key, names.toArray(new String[0]));
      redisTemplate.expire(key, Duration.ofMinutes(30));
    }

    return authorities;
  }
}
