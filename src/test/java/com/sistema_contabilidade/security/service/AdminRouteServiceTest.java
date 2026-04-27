package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
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

  @Test
  @DisplayName("Deve respeitar allowlist de IP e ignorar recursos estaticos")
  void deveRespeitarAllowlistDeIpEIgnorarRecursosEstaticos() {
    AdminRouteService service = new AdminRouteService("segredo-admin", "127.0.0.1, 10.0.0.2");

    assertTrue(service.isIpAllowed("127.0.0.1"));
    assertFalse(service.isIpAllowed("192.168.0.1"));
    assertTrue(service.isStaticResource("/assets/app.js"));
    assertTrue(service.isStaticResource("/favicon.ico"));
    assertFalse(service.isStaticResource("/admin"));
  }

  @Test
  @DisplayName("Deve gerar rotas diferentes para segredos diferentes")
  void deveGerarRotasDiferentesParaSegredosDiferentes() {
    AdminRouteService serviceA = new AdminRouteService("segredo-a", "");
    AdminRouteService serviceB = new AdminRouteService("segredo-b", "");

    assertNotEquals(serviceA.adminPagePath(), serviceB.adminPagePath());
  }
}
