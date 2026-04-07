package com.sistema_contabilidade.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.security.filter.JwtAuthFilter;
import com.sistema_contabilidade.security.filter.RateLimitFilter;
import com.sistema_contabilidade.security.filter.RequestContextMdcFilter;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

@DisplayName("SecurityConfig unit tests")
class SecurityConfigTest {

  @Test
  @DisplayName("Deve configurar CORS com origens separadas por virgula")
  void deveConfigurarCorsComOrigensSeparadasPorVirgula() {
    SecurityConfig config =
        new SecurityConfig(
            Mockito.mock(JwtAuthFilter.class),
            Mockito.mock(RateLimitFilter.class),
            Mockito.mock(RequestContextMdcFilter.class));

    CorsConfigurationSource source =
        config.corsConfigurationSource(" http://localhost:3000, http://teste.local ");
    CorsConfiguration cors = source.getCorsConfiguration(new MockHttpServletRequest());

    assertNotNull(cors);
    assertEquals(2, cors.getAllowedOrigins().size());
    assertTrue(cors.getAllowedOrigins().contains("http://localhost:3000"));
    assertTrue(cors.getAllowedOrigins().contains("http://teste.local"));
    assertTrue(cors.getAllowCredentials());
  }

  @Test
  @DisplayName("Deve validar encoder de senha")
  void deveValidarEncoderDeSenha() {
    SecurityConfig config =
        new SecurityConfig(
            Mockito.mock(JwtAuthFilter.class),
            Mockito.mock(RateLimitFilter.class),
            Mockito.mock(RequestContextMdcFilter.class));

    PasswordEncoder encoder = config.passwordEncoder();
    String raw = "senha-forte";
    String encoded = encoder.encode(raw);

    assertTrue(encoder.matches(raw, encoded));
  }

  @Test
  @DisplayName("Deve configurar cookie CSRF como HttpOnly e cabecalho customizado")
  void deveConfigurarCookieCsrfComoHttpOnlyECabecalhoCustomizado() {
    SecurityConfig config =
        new SecurityConfig(
            Mockito.mock(JwtAuthFilter.class),
            Mockito.mock(RateLimitFilter.class),
            Mockito.mock(RequestContextMdcFilter.class));

    CsrfTokenRepository repository = config.csrfTokenRepository();
    MockHttpServletRequest request = new MockHttpServletRequest();
    MockHttpServletResponse response = new MockHttpServletResponse();
    CsrfToken token = repository.generateToken(request);

    repository.saveToken(token, request, response);

    Cookie cookie = response.getCookie("XSRF-TOKEN");
    assertNotNull(cookie);
    assertTrue(cookie.isHttpOnly());
    assertEquals("/", cookie.getPath());
    assertEquals("X-CSRF-TOKEN", token.getHeaderName());
  }
}
