package com.sistema_contabilidade.rbac.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
@DisplayName("CampaignScopeResolver unit tests")
class CampaignScopeResolverTest {

  @Mock private CandidateRoleCatalogService candidateRoleCatalogService;

  @Test
  @DisplayName("Deve remover roles tecnicas e preservar campanhas do usuario")
  void deveRemoverRolesTecnicasEPreservarCampanhasDoUsuario() {
    when(candidateRoleCatalogService.filterAvailableRoles(
            Set.of("ESTAGIARIO", "CAMPANHA_A", "CAMPANHA_B")))
        .thenReturn(List.of("CAMPANHA_A", "CAMPANHA_B"));

    CampaignScope scope =
        resolver().resolve(authentication("ESTAGIARIO", "CAMPANHA_A", "CAMPANHA_B"), null);

    assertEquals(Set.of("CAMPANHA_A", "CAMPANHA_B"), scope.effectiveCampaignNames());
    assertEquals("CAMPANHA_A\u001fCAMPANHA_B", scope.canonicalCacheScope());
  }

  @Test
  @DisplayName("Deve restringir usuario multi-campanha ao filtro autorizado")
  void deveRestringirUsuarioMultiCampanhaAoFiltroAutorizado() {
    when(candidateRoleCatalogService.filterAvailableRoles(Set.of("CAMPANHA_A", "CAMPANHA_B")))
        .thenReturn(List.of("CAMPANHA_A", "CAMPANHA_B"));

    CampaignScope scope =
        resolver().resolve(authentication("CAMPANHA_A", "CAMPANHA_B"), "campanha_b");

    assertEquals(Set.of("CAMPANHA_B"), scope.effectiveCampaignNames());
    assertEquals("CAMPANHA_B", scope.canonicalCacheScope());
  }

  @Test
  @DisplayName("Deve rejeitar role tecnica e campanha fora do escopo")
  void deveRejeitarRoleTecnicaECampanhaForaDoEscopo() {
    when(candidateRoleCatalogService.filterAvailableRoles(Set.of("CAMPANHA_A", "ESTAGIARIO")))
        .thenReturn(List.of("CAMPANHA_A"));
    CampaignScopeResolver resolver = resolver();
    UsernamePasswordAuthenticationToken authentication = authentication("CAMPANHA_A", "ESTAGIARIO");

    ResponseStatusException technicalRole =
        assertThrows(
            ResponseStatusException.class, () -> resolver.resolve(authentication, "ESTAGIARIO"));
    ResponseStatusException unauthorizedCampaign =
        assertThrows(
            ResponseStatusException.class, () -> resolver.resolve(authentication, "CAMPANHA_B"));

    assertEquals(400, technicalRole.getStatusCode().value());
    assertEquals(403, unauthorizedCampaign.getStatusCode().value());
  }

  @Test
  @DisplayName("Deve dar acesso global ao admin e validar filtro no catalogo")
  void deveDarAcessoGlobalAoAdminEValidarFiltroNoCatalogo() {
    when(candidateRoleCatalogService.listAvailableRolesForAdmin())
        .thenReturn(List.of("CAMPANHA_A"));
    CampaignScopeResolver resolver = resolver();
    UsernamePasswordAuthenticationToken authentication = authentication("ADMIN");

    CampaignScope unrestricted = resolver.resolve(authentication, null);
    CampaignScope filtered = resolver.resolve(authentication, "campanha_a");
    ResponseStatusException invalidCampaign =
        assertThrows(
            ResponseStatusException.class, () -> resolver.resolve(authentication, "CAMPANHA_B"));

    assertTrue(unrestricted.allCampaigns());
    assertEquals(Set.of("CAMPANHA_A"), filtered.effectiveCampaignNames());
    assertEquals(400, invalidCampaign.getStatusCode().value());
  }

  private CampaignScopeResolver resolver() {
    return new CampaignScopeResolver(candidateRoleCatalogService);
  }

  private UsernamePasswordAuthenticationToken authentication(String... roles) {
    return new UsernamePasswordAuthenticationToken(
        "user@email.com",
        "n/a",
        java.util.Arrays.stream(roles)
            .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
            .toList());
  }
}
