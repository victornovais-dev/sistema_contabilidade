package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AdminRouteService unit tests")
class AdminRouteServiceTest {

  @Test
  @DisplayName("Deve expor rotas secretas e bloquear rotas legadas")
  void deveExporRotasSecretasEBloquearRotasLegadas() {
    AdminRouteService service = new AdminRouteService("segredo-admin", "");

    String adminPagePath = service.adminPagePath();
    String adminApiBasePath = service.adminApiBasePath();

    assertTrue(adminPagePath.startsWith("/"));
    assertTrue(service.resolveInternalPath(adminPagePath).isPresent());
    assertTrue(service.resolveInternalPath(adminApiBasePath + "/roles").isPresent());
    assertTrue(service.isLegacyAdminPath("/admin"));
    assertTrue(service.isLegacyAdminPath("/api/v1/admin/roles"));
    assertTrue(service.isLegacyAdminPath("/api/v1/usuarios"));
    assertFalse(service.isLegacyAdminPath("/api/v1/usuarios/me"));
  }
}
