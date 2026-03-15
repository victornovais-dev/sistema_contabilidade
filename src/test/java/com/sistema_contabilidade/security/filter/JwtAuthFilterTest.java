package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
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
}
