package com.sistema_contabilidade.security.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.security.service.LocalRateLimitService;
import com.sistema_contabilidade.security.service.RateLimitDecision;
import com.sistema_contabilidade.security.service.ValkeyRateLimitService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@ExtendWith(MockitoExtension.class)
@DisplayName("RateLimitFilter unit tests")
class RateLimitFilterTest {

  @Mock private ValkeyRateLimitService valkeyRateLimitService;
  @Mock private LocalRateLimitService localRateLimitService;
  @Mock private FilterChain filterChain;

  private SimpleMeterRegistry meterRegistry;
  private RateLimitFilter filter;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    filter = new RateLimitFilter(valkeyRateLimitService, localRateLimitService, meterRegistry);
  }

  @Test
  @DisplayName("Deve permitir request aceita pelo Valkey")
  void devePermitirRequestAceitaPeloValkey() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/v1/usuarios", "127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(valkeyRateLimitService.isEnabled()).thenReturn(true);
    when(valkeyRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.ALLOWED);

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(localRateLimitService);
    assertMetric("valkey", "allowed", 1.0);
  }

  @Test
  @DisplayName("Deve preservar status corpo e content type 429")
  void devePreservarContratoTooManyRequests() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/v1/usuarios", "127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(valkeyRateLimitService.isEnabled()).thenReturn(true);
    when(valkeyRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.REJECTED);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(429);
    assertThat(response.getContentType()).isEqualTo(MediaType.APPLICATION_JSON_VALUE);
    assertThat(response.getContentAsString()).isEqualTo(RateLimitFilter.TOO_MANY_REQUESTS_BODY);
    verifyNoInteractions(filterChain, localRateLimitService);
    assertMetric("valkey", "rejected", 1.0);
  }

  @Test
  @DisplayName("Falha Valkey deve acionar fallback local")
  void falhaValkeyDeveAcionarFallbackLocal() throws Exception {
    MockHttpServletRequest request = request("POST", "/api/v1/auth/login", "127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(valkeyRateLimitService.isEnabled()).thenReturn(true);
    when(valkeyRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.UNAVAILABLE);
    when(localRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.REJECTED);

    filter.doFilter(request, response, filterChain);

    assertThat(response.getStatus()).isEqualTo(429);
    verify(localRateLimitService).tryAcquire(anyString());
    verifyNoInteractions(filterChain);
    assertMetric("valkey", "error", 1.0);
    assertMetric("local", "rejected", 1.0);
  }

  @Test
  @DisplayName("Valkey recuperado deve voltar a ser backend primario")
  void valkeyRecuperadoDeveVoltarASerBackendPrimario() throws Exception {
    when(valkeyRateLimitService.isEnabled()).thenReturn(true);
    when(valkeyRateLimitService.tryAcquire(anyString()))
        .thenReturn(RateLimitDecision.UNAVAILABLE, RateLimitDecision.ALLOWED);
    when(localRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.ALLOWED);

    MockHttpServletRequest first = request("GET", "/api/v1/usuarios", "127.0.0.1");
    filter.doFilter(first, new MockHttpServletResponse(), filterChain);
    MockHttpServletRequest second = request("GET", "/api/v1/usuarios", "127.0.0.1");
    filter.doFilter(second, new MockHttpServletResponse(), filterChain);

    verify(localRateLimitService).tryAcquire(anyString());
    assertMetric("valkey", "error", 1.0);
    assertMetric("valkey", "allowed", 1.0);
    assertMetric("local", "allowed", 1.0);
  }

  @Test
  @DisplayName("Valkey desabilitado deve usar somente backend local")
  void valkeyDesabilitadoDeveUsarSomenteBackendLocal() throws Exception {
    MockHttpServletRequest request = request("GET", "/api/v1/usuarios", "127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    when(localRateLimitService.tryAcquire(anyString())).thenReturn(RateLimitDecision.ALLOWED);

    filter.doFilter(request, response, filterChain);

    verify(valkeyRateLimitService, never()).tryAcquire(anyString());
    verify(filterChain).doFilter(request, response);
    assertMetric("local", "allowed", 1.0);
  }

  @Test
  @DisplayName("Deve ignorar endpoints fora da API v1")
  void deveIgnorarEndpointsForaDaApiV1() throws Exception {
    MockHttpServletRequest request = request("GET", "/assets/app.js", "127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();

    filter.doFilter(request, response, filterChain);

    verify(filterChain).doFilter(request, response);
    verifyNoInteractions(valkeyRateLimitService, localRateLimitService);
  }

  private MockHttpServletRequest request(String method, String uri, String remoteAddress) {
    MockHttpServletRequest request = new MockHttpServletRequest(method, uri);
    request.setRemoteAddr(remoteAddress);
    return request;
  }

  private void assertMetric(String backend, String result, double expected) {
    Counter counter =
        meterRegistry
            .find(RateLimitFilter.RATE_LIMIT_METRIC)
            .tag("backend", backend)
            .tag("result", result)
            .counter();
    assertThat(counter).isNotNull();
    assertThat(counter.count()).isEqualTo(expected);
  }
}
