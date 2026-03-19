package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

@DisplayName("RequestContextMdcFilter unit tests")
class RequestContextMdcFilterTest {

  @Test
  @DisplayName("Deve gerar e devolver X-Request-ID quando header nao for informado")
  void deveGerarRequestIdQuandoHeaderAusente() throws Exception {
    RequestContextMdcFilter filter = new RequestContextMdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.setRemoteAddr("127.0.0.1");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    String traceId = response.getHeader("X-Request-ID");
    assertNotNull(traceId);
    assertEquals(32, traceId.length());
    assertNull(MDC.get("trace.id"));
  }

  @Test
  @DisplayName("Deve reutilizar X-Request-ID valido recebido")
  void deveReutilizarRequestIdValidoRecebido() throws Exception {
    RequestContextMdcFilter filter = new RequestContextMdcFilter();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.setRemoteAddr("127.0.0.1");
    request.addHeader("X-Request-ID", "0123456789abcdef0123456789abcdef");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertEquals("0123456789abcdef0123456789abcdef", response.getHeader("X-Request-ID"));
    assertNull(MDC.get("trace.id"));
  }
}
