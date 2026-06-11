package com.sistema_contabilidade.rbac.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.AuthProviderProperties;
import com.sistema_contabilidade.auth.service.CognitoGroupCatalogService;
import com.sistema_contabilidade.rbac.repository.RoleRepository;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.beans.factory.ObjectProvider;

@ExtendWith(MockitoExtension.class)
@DisplayName("CandidateRoleCatalogService unit tests")
class CandidateRoleCatalogServiceTest {

  @Mock private RoleRepository roleRepository;
  @Mock private ObjectProvider<CognitoGroupCatalogService> cognitoGroupCatalogServiceProvider;
  @Mock private CognitoGroupCatalogService cognitoGroupCatalogService;

  @Test
  @DisplayName("Deve listar candidatos do Cognito para admin")
  void deveListarCandidatosDoCognitoParaAdmin() {
    AuthProviderProperties authProviderProperties = new AuthProviderProperties();
    authProviderProperties.setProvider(AuthProvider.COGNITO);
    when(cognitoGroupCatalogServiceProvider.getIfAvailable())
        .thenReturn(cognitoGroupCatalogService);
    when(cognitoGroupCatalogService.listCandidateNormalizedGroups())
        .thenReturn(List.of("ANDRE DO PRADO", "PAULO FREIRE"));

    CandidateRoleCatalogService service =
        new CandidateRoleCatalogService(
            roleRepository, authProviderProperties, cognitoGroupCatalogServiceProvider);

    List<String> roles = service.listAvailableRolesForAdmin();

    assertEquals(List.of("ANDRE DO PRADO", "PAULO FREIRE"), roles);
    verify(roleRepository, never()).findAllRoleNamesOrdered();
  }

  @Test
  @DisplayName("Deve filtrar apenas roles candidatas do Cognito para usuario comum")
  void deveFiltrarApenasRolesCandidatasDoCognitoParaUsuarioComum() {
    AuthProviderProperties authProviderProperties = new AuthProviderProperties();
    authProviderProperties.setProvider(AuthProvider.COGNITO);
    when(cognitoGroupCatalogServiceProvider.getIfAvailable())
        .thenReturn(cognitoGroupCatalogService);
    when(cognitoGroupCatalogService.listCandidateNormalizedGroups())
        .thenReturn(List.of("ANDRE DO PRADO", "PAULO FREIRE"));

    CandidateRoleCatalogService service =
        new CandidateRoleCatalogService(
            roleRepository, authProviderProperties, cognitoGroupCatalogServiceProvider);

    List<String> roles =
        service.filterAvailableRoles(Set.of("ADMIN", "PAULO FREIRE", "TARCISIO", "SUPPORT"));

    assertEquals(List.of("PAULO FREIRE"), roles);
  }
}
