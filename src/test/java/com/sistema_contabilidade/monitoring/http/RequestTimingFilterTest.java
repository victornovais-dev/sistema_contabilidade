package com.sistema_contabilidade.monitoring.http;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@DisplayName("RequestTimingFilter unit tests")
class RequestTimingFilterTest {

  @Test
  @DisplayName("Deve adicionar headers e registrar metrica de duracao por request")
  void deveAdicionarHeadersERegistrarMetricaDeDuracaoPorRequest() throws Exception {
    RequestTimingFilter filter = defaultFilter();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    filter.setMeterRegistryForTest(meterRegistry);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/home/dashboard");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/home/dashboard");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> {};

    filter.doFilter(request, response, chain);

    String appTimeHeader = response.getHeader(RequestTimingFilter.APP_TIME_HEADER);
    String serverTimingHeader = response.getHeader(RequestTimingFilter.SERVER_TIMING_HEADER);
    assertNotNull(appTimeHeader);
    assertNotNull(serverTimingHeader);
    assertTrue(Long.parseLong(appTimeHeader) >= 0L);
    assertTrue(serverTimingHeader.startsWith("app;dur="));

    Timer timer =
        meterRegistry
            .find(RequestTimingFilter.REQUEST_DURATION_METRIC)
            .tags("method", "GET", "uri", "/api/v1/home/dashboard", "status", "200")
            .timer();
    assertNotNull(timer);
    assertEquals(1L, timer.count());
    assertTrue(timer.totalTime(java.util.concurrent.TimeUnit.MILLISECONDS) >= 0.0d);
  }

  @Test
  @DisplayName("Deve ignorar assets estaticos")
  void deveIgnorarAssetsEstaticos() throws Exception {
    RequestTimingFilter filter = defaultFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/js/app.js");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> {};

    filter.doFilter(request, response, chain);

    assertNull(response.getHeader(RequestTimingFilter.APP_TIME_HEADER));
    assertNull(response.getHeader(RequestTimingFilter.SERVER_TIMING_HEADER));
  }

  private RequestTimingFilter defaultFilter() {
    RequestTimingFilter filter = new RequestTimingFilter();
    filter.setEnabled(true);
    filter.setAddResponseHeaders(true);
    filter.setLogThresholdMs(Long.MAX_VALUE);
    return filter;
  }
}
