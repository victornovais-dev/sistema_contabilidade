package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.security.service.AdminRouteService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("AdminRouteFilter unit tests")
class AdminRouteFilterTest {

  @Mock private AdminRouteService adminRouteService;
  @Mock private RequestFingerprintService requestFingerprintService;

  @Test
  @DisplayName("Deve ignorar recurso estatico")
  void shouldNotFilterDeveIgnorarRecursoEstatico() {
    AdminRouteFilter filter = new AdminRouteFilter(adminRouteService, requestFingerprintService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/app.js");
    when(adminRouteService.isStaticResource("/assets/app.js")).thenReturn(true);

    assertTrue(filter.shouldNotFilter(request));
  }

  @Test
  @DisplayName("Deve reescrever caminho interno quando rota secreta for valida")
  void deveReescreverCaminhoInternoQuandoRotaSecretaForValida() throws Exception {
    AdminRouteFilter filter = new AdminRouteFilter(adminRouteService, requestFingerprintService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/segredo/admin");
    MockHttpServletResponse response = new MockHttpServletResponse();
    CapturingFilterChain filterChain = new CapturingFilterChain();

    when(adminRouteService.isStaticResource("/segredo/admin")).thenReturn(false);
    when(adminRouteService.resolveInternalPath("/segredo/admin")).thenReturn(Optional.of("/admin"));
    when(requestFingerprintService.resolveClientIp(request)).thenReturn("127.0.0.1");
    when(adminRouteService.isIpAllowed("127.0.0.1")).thenReturn(true);

    filter.doFilter(request, response, filterChain);

    assertEquals("/admin", filterChain.request.getRequestURI());
    assertEquals("/admin", filterChain.request.getServletPath());
    assertTrue(filterChain.request.getRequestURL().toString().endsWith("/admin"));
  }

  @Test
  @DisplayName("Deve responder 404 json para rota admin legada de API")
  void deveResponder404JsonParaRotaAdminLegadaDeApi() throws Exception {
    AdminRouteFilter filter = new AdminRouteFilter(adminRouteService, requestFingerprintService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/admin/roles");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(adminRouteService.isStaticResource("/api/v1/admin/roles")).thenReturn(false);
    when(adminRouteService.resolveInternalPath("/api/v1/admin/roles")).thenReturn(Optional.empty());
    when(adminRouteService.isLegacyAdminPath("/api/v1/admin/roles")).thenReturn(true);

    filter.doFilter(request, response, new CapturingFilterChain());

    assertEquals(404, response.getStatus());
    assertEquals(
        "{\"status\":404,\"message\":\"Recurso nao encontrado\"}", response.getContentAsString());
  }

  @Test
  @DisplayName("Deve redirecionar para 404 quando IP nao for permitido")
  void deveRedirecionarPara404QuandoIpNaoForPermitido() throws Exception {
    AdminRouteFilter filter = new AdminRouteFilter(adminRouteService, requestFingerprintService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/segredo/admin");
    MockHttpServletResponse response = new MockHttpServletResponse();

    when(adminRouteService.isStaticResource("/segredo/admin")).thenReturn(false);
    when(adminRouteService.resolveInternalPath("/segredo/admin")).thenReturn(Optional.of("/admin"));
    when(requestFingerprintService.resolveClientIp(request)).thenReturn("10.0.0.1");
    when(adminRouteService.isIpAllowed("10.0.0.1")).thenReturn(false);

    filter.doFilter(request, response, new CapturingFilterChain());

    assertEquals("/404", response.getRedirectedUrl());
    verify(adminRouteService).isIpAllowed("10.0.0.1");
  }

  private static final class CapturingFilterChain implements FilterChain {

    private HttpServletRequest request;

    @Override
    public void doFilter(
        jakarta.servlet.ServletRequest request, jakarta.servlet.ServletResponse response) {
      this.request = (HttpServletRequest) request;
    }
  }
}
