package com.sistema_contabilidade.auth.service;

import java.util.Set;

public record CognitoUserProfile(
    String providerUsername, String email, String nome, String cognitoSub, Set<String> groups) {

  public CognitoUserProfile {
    groups = groups == null ? Set.of() : Set.copyOf(groups);
  }
}
