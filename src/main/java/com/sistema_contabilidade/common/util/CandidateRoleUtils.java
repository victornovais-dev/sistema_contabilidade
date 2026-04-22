package com.sistema_contabilidade.common.util;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class CandidateRoleUtils {

  private static final Set<String> TECHNICAL_ROLES =
      Set.of("ADMIN", "CONTABIL", "MANAGER", "SUPPORT", "CANDIDATO");

  private CandidateRoleUtils() {}

  public static List<String> filterCandidateRoles(Collection<String> roles) {
    if (roles == null || roles.isEmpty()) {
      return List.of();
    }
    return roles.stream()
        .map(CandidateRoleUtils::normalizeRole)
        .filter(CandidateRoleUtils::isCandidateRole)
        .distinct()
        .sorted()
        .toList();
  }

  public static boolean isCandidateRole(String role) {
    String normalizedRole = normalizeRole(role);
    return !normalizedRole.isBlank() && !TECHNICAL_ROLES.contains(normalizedRole);
  }

  private static String normalizeRole(String role) {
    if (role == null) {
      return "";
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }
}
