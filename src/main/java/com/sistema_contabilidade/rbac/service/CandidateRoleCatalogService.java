package com.sistema_contabilidade.rbac.service;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.AuthProviderProperties;
import com.sistema_contabilidade.auth.service.CognitoGroupCatalogService;
import com.sistema_contabilidade.common.util.CandidateRoleUtils;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CandidateRoleCatalogService {

  private final RoleRepository roleRepository;
  private final AuthProviderProperties authProviderProperties;
  private final ObjectProvider<CognitoGroupCatalogService> cognitoGroupCatalogServiceProvider;

  public List<String> listAvailableRolesForAdmin() {
    if (authProviderProperties.getProvider() == AuthProvider.COGNITO) {
      return requireCognitoGroupCatalogService().listCandidateNormalizedGroups();
    }
    return CandidateRoleUtils.filterCandidateRoles(roleRepository.findAllRoleNamesOrdered());
  }

  public List<String> filterAvailableRoles(Collection<String> roleNames) {
    if (roleNames == null || roleNames.isEmpty()) {
      return List.of();
    }
    if (authProviderProperties.getProvider() == AuthProvider.COGNITO) {
      List<String> candidateRoles =
          requireCognitoGroupCatalogService().listCandidateNormalizedGroups();
      return roleNames.stream()
          .map(CandidateRoleCatalogService::normalizeRole)
          .filter(candidateRoles::contains)
          .distinct()
          .sorted()
          .toList();
    }
    return CandidateRoleUtils.filterCandidateRoles(roleNames);
  }

  private CognitoGroupCatalogService requireCognitoGroupCatalogService() {
    CognitoGroupCatalogService cognitoGroupCatalogService =
        cognitoGroupCatalogServiceProvider.getIfAvailable();
    if (cognitoGroupCatalogService == null) {
      throw new IllegalStateException("CognitoGroupCatalogService indisponivel");
    }
    return cognitoGroupCatalogService;
  }

  private static String normalizeRole(String role) {
    if (role == null) {
      return "";
    }
    return role.trim().toUpperCase(Locale.ROOT);
  }
}
