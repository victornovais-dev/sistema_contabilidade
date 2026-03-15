package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RateLimitFilter unit tests")
class RateLimitFilterTest {

  @Test
  @DisplayName("Deve retornar 429 ao exceder limite")
  void deveRetornarTooManyRequestsQuandoExcederLimite() throws Exception {
    RateLimitFilter filter = new RateLimitFilter(1, 60);
    MockFilterChain chain = new MockFilterChain();

    MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    req1.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse res1 = new MockHttpServletResponse();
    filter.doFilter(req1, res1, chain);
    assertEquals(200, res1.getStatus());

    MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    req2.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse res2 = new MockHttpServletResponse();
    filter.doFilter(req2, res2, chain);

    assertEquals(429, res2.getStatus());
  }
}
