package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import java.util.Set;

public record AuthProviderRefreshResult(
    AuthProvider provider,
    String providerUsername,
    String email,
    String nome,
    String cognitoSub,
    Set<String> groups,
    String refreshToken) {

  public AuthProviderRefreshResult {
    groups = groups == null ? Set.of() : Set.copyOf(groups);
  }
}
