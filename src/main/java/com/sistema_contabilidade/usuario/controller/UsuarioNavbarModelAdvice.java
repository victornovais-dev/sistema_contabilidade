package com.sistema_contabilidade.usuario.controller;

import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ModelAttribute;

@ControllerAdvice
public class UsuarioNavbarModelAdvice {

  private static final String VICTOR_NOVAIS_EMAIL = "victornovais77@gmail.com";
  private static final String LUCAS_CLARIGEST_EMAIL = "lucas@clarigest.com";
  private static final String VICTOR_CAMPAIGN_THEME = "victor-campaign";

  @ModelAttribute("usuarioRoles")
  public Set<String> usuarioRoles(Authentication authentication) {
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .filter(authority -> authority != null && authority.startsWith("ROLE_"))
        .map(authority -> authority.substring("ROLE_".length()))
        .collect(Collectors.toUnmodifiableSet());
  }

  @ModelAttribute("accountTheme")
  public String accountTheme(Authentication authentication) {
    if (authentication == null) {
      return null;
    }

    String email = authentication.getName();
    if (VICTOR_NOVAIS_EMAIL.equalsIgnoreCase(email)
        || LUCAS_CLARIGEST_EMAIL.equalsIgnoreCase(email)) {
      return VICTOR_CAMPAIGN_THEME;
    }
    return null;
  }
}
