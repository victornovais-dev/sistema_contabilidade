package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import java.util.Set;
import java.util.UUID;

public record AuthProviderLoginResult(
    AuthProvider provider,
    UUID localUserId,
    String providerUsername,
    String email,
    String nome,
    String cognitoSub,
    Set<String> groups,
    String refreshToken,
    AuthLoginChallenge challenge) {

  public AuthProviderLoginResult {
    groups = groups == null ? Set.of() : Set.copyOf(groups);
  }

  public boolean challengeRequired() {
    return challenge != null;
  }
}
