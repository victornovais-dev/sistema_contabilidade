package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import java.util.Set;
import java.util.UUID;

public record SessionCreationRequest(
    UUID usuarioId,
    AuthProvider authProvider,
    String authUsername,
    String cognitoSub,
    String refreshToken,
    Set<String> groups,
    String groupsHash) {

  public SessionCreationRequest {
    authProvider = authProvider == null ? AuthProvider.LOCAL : authProvider;
    groups = groups == null ? Set.of() : Set.copyOf(groups);
  }
}
