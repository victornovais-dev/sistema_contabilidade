package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.usuario.model.Usuario;
import com.sistema_contabilidade.usuario.repository.UsuarioRepository;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;

public final class ItemAccessUtils {

  private static final String ADMIN_AUTHORITY = "ROLE_ADMIN";

  private ItemAccessUtils() {}

  public static boolean isAdmin(Authentication authentication) {
    if (authentication == null) {
      return false;
    }
    return authentication.getAuthorities().stream()
        .anyMatch(authority -> ADMIN_AUTHORITY.equals(authority.getAuthority()));
  }

  public static Usuario buscarUsuarioAutenticado(
      Authentication authentication, UsuarioRepository usuarioRepository) {
    if (authentication == null || authentication.getName() == null) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao autenticado");
    }
    return usuarioRepository
        .findByEmail(authentication.getName())
        .orElseThrow(
            () -> new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Usuario nao encontrado"));
  }

  public static Set<String> extrairRoleNomes(Usuario usuario) {
    return usuario.getRoles().stream()
        .map(role -> role.getNome())
        .map(ItemAccessUtils::normalizarRole)
        .filter(java.util.Objects::nonNull)
        .collect(Collectors.toSet());
  }

  public static void validarRoleFiltro(String roleFiltro, Set<String> roleNomesUsuario) {
    if (!roleNomesUsuario.contains(roleFiltro)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A role selecionada nao pertence ao usuario autenticado.");
    }
  }

  public static String normalizarRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }
}
