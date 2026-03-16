package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.User;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtAuthFilter unit tests")
class JwtAuthFilterTest {

  @Mock private JwtService jwtService;
  @Mock private CustomUserDetailsService userDetailsService;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Deve seguir cadeia sem autenticar quando header Authorization não existe")
  void deveSeguirSemAutenticarQuandoHeaderNaoExiste() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(jwtService, never()).extractUsername(anyString());
  }

  @Test
  @DisplayName("Deve autenticar quando token válido e sem autenticação prévia")
  void deveAutenticarQuandoTokenValidoESemAutenticacaoPrevia() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(jwtService.extractUsername("token-valido")).thenReturn("ana@email.com");
    when(userDetailsService.loadUserByUsername("ana@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails)).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("ana@email.com");
  }

  @Test
  @DisplayName("Não deve autenticar quando token é inválido")
  void naoDeveAutenticarQuandoTokenInvalido() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-invalido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(jwtService.extractUsername("token-invalido")).thenReturn("ana@email.com");
    when(userDetailsService.loadUserByUsername("ana@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-invalido", userDetails)).thenReturn(false);

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Não deve carregar usuário quando contexto já está autenticado")
  void naoDeveCarregarUsuarioQuandoContextoJaAutenticado() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("existing", null));
    when(jwtService.extractUsername("token-valido")).thenReturn("ana@email.com");

    filter.doFilter(request, response, chain);

    verify(userDetailsService, never()).loadUserByUsername(anyString());
  }

  @Test
  @DisplayName("Deve autenticar com token recebido por cookie quando header Authorization ausente")
  void deveAutenticarComTokenPorCookie() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.setCookies(new Cookie("SC_TOKEN", "token-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("admin@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(jwtService.extractUsername("token-cookie")).thenReturn("admin@email.com");
    when(userDetailsService.loadUserByUsername("admin@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-cookie", userDetails)).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("admin@email.com");
  }

  @Test
  @DisplayName("Nao deve autenticar quando Bearer vem vazio")
  void naoDeveAutenticarQuandoBearerVazio() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(jwtService, never()).extractUsername(anyString());
  }

  @Test
  @DisplayName("Deve usar cookie quando Authorization nao eh Bearer")
  void deveUsarCookieQuandoAuthorizationNaoEhBearer() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.addHeader("Authorization", "Basic abc123");
    request.setCookies(new Cookie("SC_TOKEN", "token-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("admin@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(jwtService.extractUsername("token-cookie")).thenReturn("admin@email.com");
    when(userDetailsService.loadUserByUsername("admin@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-cookie", userDetails)).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("admin@email.com");
  }

  @Test
  @DisplayName("Nao deve autenticar quando token de cookie nao corresponde ao nome esperado")
  void naoDeveAutenticarQuandoCookieNaoCorresponde() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.setCookies(new Cookie("OUTRO_TOKEN", "token-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(jwtService, never()).extractUsername(anyString());
  }

  @Test
  @DisplayName("Nao deve autenticar quando username extraido for nulo")
  void naoDeveAutenticarQuandoUsernameExtraidoForNulo() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-sem-usuario");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    when(jwtService.extractUsername("token-sem-usuario")).thenReturn(null);

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService, never()).loadUserByUsername(anyString());
  }

  @Test
  @DisplayName("Nao deve quebrar quando token estiver expirado e deve limpar cookie")
  void naoDeveQuebrarQuandoTokenExpirado() throws Exception {
    JwtAuthFilter filter = new JwtAuthFilter(jwtService, userDetailsService);
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.setCookies(new Cookie("SC_TOKEN", "token-expirado"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    when(jwtService.extractUsername("token-expirado"))
        .thenThrow(new ExpiredJwtException(null, null, "Token expirado"));

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService, never()).loadUserByUsername(anyString());
    Cookie cookieLimpo = response.getCookie("SC_TOKEN");
    assertNotNull(cookieLimpo);
    assertEquals(0, cookieLimpo.getMaxAge());
    assertEquals("/", cookieLimpo.getPath());
    assertTrue(cookieLimpo.getSecure());
  }
}
