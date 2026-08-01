package com.sistema_contabilidade.rbac.service;

import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
@RequiredArgsConstructor
public class CampaignScopeResolver {

  private static final String ADMIN_ROLE = "ADMIN";

  private final CandidateRoleCatalogService candidateRoleCatalogService;

  public CampaignScope resolve(Authentication authentication, String roleFilter) {
    String normalizedRoleFilter = normalizeRole(roleFilter);
    Set<String> authorityNames = authorityNames(authentication);
    if (authorityNames.contains(ADMIN_ROLE)) {
      return resolveAdminScope(normalizedRoleFilter);
    }

    Set<String> campaignNames =
        Set.copyOf(candidateRoleCatalogService.filterAvailableRoles(authorityNames));
    if (normalizedRoleFilter == null) {
      return CampaignScope.restricted(campaignNames);
    }
    validateNonAdminFilter(normalizedRoleFilter, campaignNames);
    return CampaignScope.restricted(campaignNames).withRoleFilter(normalizedRoleFilter);
  }

  public List<String> listAvailableCampaigns(Authentication authentication) {
    if (authorityNames(authentication).contains(ADMIN_ROLE)) {
      return candidateRoleCatalogService.listAvailableRolesForAdmin();
    }
    return candidateRoleCatalogService.filterAvailableRoles(authorityNames(authentication));
  }

  private CampaignScope resolveAdminScope(String normalizedRoleFilter) {
    if (normalizedRoleFilter == null) {
      return CampaignScope.all();
    }
    List<String> availableCampaigns = candidateRoleCatalogService.listAvailableRolesForAdmin();
    if (!availableCampaigns.contains(normalizedRoleFilter)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A campanha selecionada e invalida.");
    }
    return CampaignScope.all().withRoleFilter(normalizedRoleFilter);
  }

  private void validateNonAdminFilter(String normalizedRoleFilter, Set<String> campaignNames) {
    if (!CandidateRoleUtils.isCandidateRole(normalizedRoleFilter)) {
      throw new ResponseStatusException(
          HttpStatus.BAD_REQUEST, "A role selecionada nao e uma campanha.");
    }
    if (!campaignNames.contains(normalizedRoleFilter)) {
      throw new ResponseStatusException(
          HttpStatus.FORBIDDEN, "A campanha selecionada nao pertence ao usuario autenticado.");
    }
  }

  private Set<String> authorityNames(Authentication authentication) {
    if (authentication == null) {
      return Set.of();
    }
    return authentication.getAuthorities().stream()
        .map(GrantedAuthority::getAuthority)
        .map(CampaignScopeResolver::authorityName)
        .filter(role -> !role.isEmpty())
        .collect(Collectors.toSet());
  }

  private static String authorityName(String authority) {
    if (authority == null || !authority.startsWith("ROLE_")) {
      return "";
    }
    return normalizeRole(authority.substring("ROLE_".length()));
  }

  private static String normalizeRole(String role) {
    if (role == null || role.isBlank()) {
      return null;
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }
}
