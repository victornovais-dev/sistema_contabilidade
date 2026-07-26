package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("DatabaseRoutingFilter unit tests")
class DatabaseRoutingFilterTest {

  private final DatabaseRoutingFilter filter = new DatabaseRoutingFilter();

  @AfterEach
  void clearContext() {
    DatabaseRoutingContext.clear();
  }

  @Test
  @DisplayName("Deve permitir reader para GET com sessao validada")
  void devePermitirReaderParaGetComSessaoValidada() throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> assertTrue(DatabaseRoutingContext.isReaderAllowed()));

    assertFalse(DatabaseRoutingContext.isReaderAllowed());
  }

  @Test
  @DisplayName("Deve permitir reader para HEAD com sessao validada")
  void devePermitirReaderParaHeadComSessaoValidada() throws Exception {
    MockHttpServletRequest request = authenticatedRequest("HEAD");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> assertTrue(DatabaseRoutingContext.isReaderAllowed()));

    assertFalse(DatabaseRoutingContext.isReaderAllowed());
  }

  @Test
  @DisplayName("Deve forcar writer para mutacao autenticada")
  void deveForcarWriterParaMutacaoAutenticada() throws Exception {
    MockHttpServletRequest request = authenticatedRequest("POST");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> assertFalse(DatabaseRoutingContext.isReaderAllowed()));
  }

  @Test
  @DisplayName("Deve forcar writer para Bearer sem sessao validada")
  void deveForcarWriterParaBearerSemSessaoValidada() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    request.addHeader("Authorization", "Bearer token");

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> assertFalse(DatabaseRoutingContext.isReaderAllowed()));
  }

  @Test
  @DisplayName("Deve limpar ThreadLocal quando cadeia falha")
  void deveLimparThreadLocalQuandoCadeiaFalha() {
    MockHttpServletRequest request = authenticatedRequest("GET");

    assertThrows(
        ServletException.class,
        () ->
            filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                  throw new ServletException("falha");
                }));

    assertFalse(DatabaseRoutingContext.isReaderAllowed());
  }

  private MockHttpServletRequest authenticatedRequest(String method) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, "/api/v1/itens");
    request.setAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE, UUID.randomUUID());
    return request;
  }
}
