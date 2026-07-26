package com.sistema_contabilidade.security.config;

import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.security.filter.DatabaseRoutingFilter;
import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import com.sistema_contabilidade.security.filter.RequestContextMdcFilter;
import jakarta.servlet.Filter;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.web.FilterChainProxy;
import org.springframework.security.web.SecurityFilterChain;

@SpringBootTest
@DisplayName("Security filter order integration tests")
class SecurityFilterOrderIntegrationTest {

  @Autowired private FilterChainProxy filterChainProxy;

  @Test
  @DisplayName("Deve executar rate limit, JWT, routing e MDC nesta ordem")
  void deveExecutarFiltrosNaOrdemDefinida() {
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    SecurityFilterChain matchingChain =
        filterChainProxy.getFilterChains().stream()
            .filter(chain -> chain.matches(request))
            .findFirst()
            .orElseThrow();
    List<Filter> filters = matchingChain.getFilters();

    int rateLimitIndex = indexOf(filters, RateLimitFilter.class);
    int jwtIndex = indexOf(filters, JwtAuthFilter.class);
    int routingIndex = indexOf(filters, DatabaseRoutingFilter.class);
    int mdcIndex = indexOf(filters, RequestContextMdcFilter.class);

    assertTrue(rateLimitIndex < jwtIndex);
    assertTrue(jwtIndex < routingIndex);
    assertTrue(routingIndex < mdcIndex);
  }

  private int indexOf(List<Filter> filters, Class<? extends Filter> filterType) {
    for (int index = 0; index < filters.size(); index++) {
      if (filterType.isInstance(filters.get(index))) {
        return index;
      }
    }
    throw new AssertionError("Filtro ausente: " + filterType.getSimpleName());
  }
}
