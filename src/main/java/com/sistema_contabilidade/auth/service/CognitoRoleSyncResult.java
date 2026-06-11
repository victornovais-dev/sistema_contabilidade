package com.sistema_contabilidade.auth.service;

import java.util.Set;

public record CognitoRoleSyncResult(Set<String> normalizedGroups, String groupsHash) {

  public CognitoRoleSyncResult {
    normalizedGroups = normalizedGroups == null ? Set.of() : Set.copyOf(normalizedGroups);
  }
}
