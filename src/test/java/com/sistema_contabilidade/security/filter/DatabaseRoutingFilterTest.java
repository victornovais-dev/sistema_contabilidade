package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.database.service.StickyWriterService;
import com.sistema_contabilidade.item.service.ItemListPageCache;
import com.sistema_contabilidade.relatorio.service.RelatorioResumoCacheService;
import jakarta.servlet.ServletException;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseCookie;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("DatabaseRoutingFilter unit tests")
class DatabaseRoutingFilterTest {

  @Mock private StickyWriterService stickyWriterService;
  @Mock private RelatorioResumoCacheService relatorioResumoCacheService;
  @Mock private ItemListPageCache itemListPageCache;

  @InjectMocks private DatabaseRoutingFilter filter;

  @AfterEach
  void clearContext() {
    DatabaseRoutingContext.clear();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "HEAD"})
  @DisplayName("Deve permitir reader para leitura com sessao sem sticky")
  void devePermitirReaderParaLeituraComSessaoSemSticky(String method) throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest(method, "/api/v1/itens", sessionId);
    when(stickyWriterService.requiresWriter(sessionId, null)).thenReturn(false);

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> {
          assertTrue(DatabaseRoutingContext.isReaderAllowed());
          assertFalse(DatabaseRoutingContext.isStickyWriter());
        });

    assertContextCleared();
  }

  @Test
  @DisplayName("Deve forcar writer para leitura durante sticky")
  void deveForcarWriterParaLeituraDuranteSticky() throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("GET", "/api/v1/itens", sessionId);
    when(stickyWriterService.requiresWriter(sessionId, null)).thenReturn(true);

    filter.doFilter(
        request,
        new MockHttpServletResponse(),
        (servletRequest, servletResponse) -> {
          assertFalse(DatabaseRoutingContext.isReaderAllowed());
          assertTrue(DatabaseRoutingContext.isStickyWriter());
        });

    assertContextCleared();
  }

  @ParameterizedTest
  @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
  @DisplayName("Deve renovar sticky depois de mutacao autenticada 2xx")
  void deveRenovarStickyDepoisDeMutacaoAutenticada2xx(String method) throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest(method, "/api/v1/itens", sessionId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request,
        response,
        (servletRequest, servletResponse) -> {
          assertFalse(DatabaseRoutingContext.isReaderAllowed());
          response.setStatus(204);
        });

    verify(stickyWriterService).markWriter(sessionId);
    verify(relatorioResumoCacheService).invalidateAfterItemWrite();
    verify(itemListPageCache).invalidateAfterItemWrite();
    assertContextCleared();
  }

  @Test
  @DisplayName("Deve enviar marcador sticky HttpOnly depois de mutacao autenticada")
  void deveEnviarMarcadorStickyDepoisDeMutacaoAutenticada() throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("POST", "/api/v1/itens", sessionId);
    request.addHeader("X-Forwarded-Proto", "https");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(stickyWriterService.signedMarkerCookie(sessionId, true))
        .thenReturn(
            java.util.Optional.of(
                ResponseCookie.from("SC_DB_STICKY", "marcador-assinado")
                    .httpOnly(true)
                    .secure(true)
                    .sameSite("Strict")
                    .path("/")
                    .maxAge(10)
                    .build()));

    filter.doFilter(
        request, response, (servletRequest, servletResponse) -> response.setStatus(204));

    assertTrue(
        response.getHeaders("Set-Cookie").stream()
            .anyMatch(value -> value.contains("SC_DB_STICKY=marcador-assinado")));
  }

  @Test
  @DisplayName("Nao deve invalidar resumo apos mutacao fora de itens")
  void naoDeveInvalidarResumoAposMutacaoForaDeItens() throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("POST", "/api/v1/usuarios", sessionId);

    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});

    verify(relatorioResumoCacheService, never()).invalidateAfterItemWrite();
    verify(itemListPageCache, never()).invalidateAfterItemWrite();
  }

  @Test
  @DisplayName("Deve invalidar resumo apos mutacao de item via Bearer sem sessao")
  void deveInvalidarResumoAposMutacaoDeItemViaBearerSemSessao() throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/itens");
    request.addHeader("Authorization", "Bearer token");

    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});

    verify(stickyWriterService, never()).markWriter(Mockito.any());
    verify(relatorioResumoCacheService).invalidateAfterItemWrite();
    verify(itemListPageCache).invalidateAfterItemWrite();
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/auth/refresh", "/api/v1/auth/logout"})
  @DisplayName("Deve renovar sticky em refresh e logout com sessao validada")
  void deveRenovarStickyEmRefreshELogoutComSessaoValidada(String uri) throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("POST", uri, sessionId);
    if (uri.endsWith("/logout")) {
      when(stickyWriterService.clearSignedMarkerCookie(false))
          .thenReturn(ResponseCookie.from("SC_DB_STICKY", "").path("/").maxAge(0).build());
    }

    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});

    verify(stickyWriterService).markWriter(sessionId);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/api/v1/auth/login", "/api/v1/auth/complete-new-password"})
  @DisplayName("Nao deve marcar login sem sessao validada")
  void naoDeveMarcarLoginSemSessaoValidada(String uri) throws Exception {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", uri);

    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});

    verifyNoInteractions(stickyWriterService);
  }

  @ParameterizedTest
  @ValueSource(strings = {"/assets/app.js", "/actuator/health", "/favicon.ico"})
  @DisplayName("Nao deve consultar sticky para assets e actuator")
  void naoDeveConsultarStickyParaAssetsEActuator(String uri) throws Exception {
    MockHttpServletRequest request = authenticatedRequest("GET", uri, UUID.randomUUID());

    filter.doFilter(
        request, new MockHttpServletResponse(), (servletRequest, servletResponse) -> {});

    verifyNoInteractions(stickyWriterService);
  }

  @Test
  @DisplayName("Nao deve renovar sticky quando mutacao falha")
  void naoDeveRenovarStickyQuandoMutacaoFalha() throws Exception {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("POST", "/api/v1/itens", sessionId);
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(
        request, response, (servletRequest, servletResponse) -> response.setStatus(400));

    verify(stickyWriterService, never()).markWriter(sessionId);
    verify(relatorioResumoCacheService, never()).invalidateAfterItemWrite();
    verify(itemListPageCache, never()).invalidateAfterItemWrite();
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

    verifyNoInteractions(stickyWriterService);
  }

  @Test
  @DisplayName("Deve limpar ThreadLocal quando cadeia falha")
  void deveLimparThreadLocalQuandoCadeiaFalha() {
    UUID sessionId = UUID.randomUUID();
    MockHttpServletRequest request = authenticatedRequest("GET", "/api/v1/itens", sessionId);
    when(stickyWriterService.requiresWriter(sessionId, null)).thenReturn(true);

    assertThrows(
        ServletException.class,
        () ->
            filter.doFilter(
                request,
                new MockHttpServletResponse(),
                (servletRequest, servletResponse) -> {
                  throw new ServletException("falha");
                }));

    verify(stickyWriterService, never()).markWriter(sessionId);
    assertContextCleared();
  }

  private MockHttpServletRequest authenticatedRequest(String method, String uri, UUID sessionId) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE, sessionId);
    return request;
  }

  private void assertContextCleared() {
    assertFalse(DatabaseRoutingContext.isReaderAllowed());
    assertFalse(DatabaseRoutingContext.isStickyWriter());
  }
}
