package com.sistema_contabilidade.monitoring.query;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.servlet.HandlerMapping;

@DisplayName("QueryCountFilter unit tests")
class QueryCountFilterTest {

  @AfterEach
  void tearDown() {
    QueryCountContext.clear();
  }

  @Test
  @DisplayName("Deve adicionar header com total de consultas da request")
  void deveAdicionarHeaderComTotalDeConsultasDaRequest() throws Exception {
    QueryCountFilter filter = defaultFilter();
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    filter.setMeterRegistryForTest(meterRegistry);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    request.setAttribute(HandlerMapping.BEST_MATCHING_PATTERN_ATTRIBUTE, "/api/v1/itens");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain =
        (servletRequest, servletResponse) -> {
          QueryCountContext.increment();
          QueryCountContext.increment();
        };

    filter.doFilter(request, response, chain);

    assertEquals("2", response.getHeader(QueryCountFilter.QUERY_COUNT_HEADER));
    assertEquals(0, QueryCountContext.get());

    DistributionSummary summary =
        meterRegistry
            .find(QueryCountFilter.QUERY_COUNT_METRIC)
            .tags("method", "GET", "uri", "/api/v1/itens", "status", "200")
            .summary();
    assertNotNull(summary);
    assertEquals(1, summary.count());
    assertEquals(2.0d, summary.totalAmount());
  }

  @Test
  @DisplayName("Deve ignorar assets estaticos")
  void deveIgnorarAssetsEstaticos() throws Exception {
    QueryCountFilter filter = defaultFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/assets/css/styles.css");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> QueryCountContext.increment();

    filter.doFilter(request, response, chain);

    assertNull(response.getHeader(QueryCountFilter.QUERY_COUNT_HEADER));
    assertEquals(0, QueryCountContext.get());
  }

  @Test
  @DisplayName("Deve respeitar monitor desabilitado")
  void deveRespeitarMonitorDesabilitado() throws Exception {
    QueryCountFilter filter = defaultFilter();
    filter.setEnabled(false);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    MockHttpServletResponse response = new MockHttpServletResponse();
    FilterChain chain = (servletRequest, servletResponse) -> QueryCountContext.increment();

    filter.doFilter(request, response, chain);

    assertNull(response.getHeader(QueryCountFilter.QUERY_COUNT_HEADER));
    assertEquals(0, QueryCountContext.get());
  }

  private QueryCountFilter defaultFilter() {
    QueryCountFilter filter = new QueryCountFilter();
    filter.setEnabled(true);
    filter.setThreshold(15);
    filter.setAddResponseHeader(true);
    return filter;
  }
}
