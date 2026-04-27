package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.service.SessaoUsuarioService;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import com.sistema_contabilidade.security.service.JwtService;
import com.sistema_contabilidade.security.service.RequestFingerprintService;
import io.jsonwebtoken.ExpiredJwtException;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
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
  @Mock private SessaoUsuarioService sessaoUsuarioService;
  @Mock private RequestFingerprintService requestFingerprintService;

  @AfterEach
  void clearContext() {
    SecurityContextHolder.clearContext();
  }

  @Test
  @DisplayName("Deve seguir cadeia sem autenticar quando nao existe credencial")
  void deveSeguirSemAutenticarQuandoNaoExisteCredencial() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(jwtService, never()).extractUsername(anyString());
  }

  @Test
  @DisplayName("Deve autenticar quando token valido vem no header Authorization")
  void deveAutenticarQuandoTokenValidoVemNoHeaderAuthorization() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUsername("token-valido")).thenReturn("ana@email.com");
    when(userDetailsService.loadUserByUsername("ana@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails, "fingerprint")).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("ana@email.com");
  }

  @Test
  @DisplayName("Nao deve autenticar quando token e invalido")
  void naoDeveAutenticarQuandoTokenEInvalido() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-invalido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUsername("token-invalido")).thenReturn("ana@email.com");
    when(userDetailsService.loadUserByUsername("ana@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-invalido", userDetails, "fingerprint")).thenReturn(false);

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
  }

  @Test
  @DisplayName("Nao deve carregar usuario quando contexto ja esta autenticado")
  void naoDeveCarregarUsuarioQuandoContextoJaEstaAutenticado() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    SecurityContextHolder.getContext()
        .setAuthentication(new UsernamePasswordAuthenticationToken("existing", null));

    filter.doFilter(request, response, chain);

    verify(userDetailsService, never()).loadUserByUsername(anyString());
  }

  @Test
  @DisplayName("Deve autenticar com token legado recebido por cookie")
  void deveAutenticarComTokenLegadoRecebidoPorCookie() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.setCookies(new Cookie("SC_TOKEN", "token-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("admin@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUsername("token-cookie")).thenReturn("admin@email.com");
    when(userDetailsService.loadUserByUsername("admin@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-cookie", userDetails, "fingerprint")).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("admin@email.com");
  }

  @Test
  @DisplayName("Nao deve autenticar quando Bearer vem vazio")
  void naoDeveAutenticarQuandoBearerVemVazio() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer ");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    verify(jwtService, never()).extractUsername(anyString());
  }

  @Test
  @DisplayName("Deve usar cookie legado quando Authorization nao eh Bearer")
  void deveUsarCookieLegadoQuandoAuthorizationNaoEhBearer() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.addHeader("Authorization", "Basic abc123");
    request.setCookies(new Cookie("SC_TOKEN", "token-cookie"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("admin@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUsername("token-cookie")).thenReturn("admin@email.com");
    when(userDetailsService.loadUserByUsername("admin@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-cookie", userDetails, "fingerprint")).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserByUsername("admin@email.com");
  }

  @Test
  @DisplayName("Nao deve autenticar quando cookie nao corresponde aos nomes esperados")
  void naoDeveAutenticarQuandoCookieNaoCorrespondeAosNomesEsperados() throws Exception {
    JwtAuthFilter filter = novoFiltro();
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
    JwtAuthFilter filter = novoFiltro();
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
  @DisplayName("Nao deve quebrar quando token legado estiver expirado e deve limpar cookie")
  void naoDeveQuebrarQuandoTokenLegadoEstiverExpirado() throws Exception {
    JwtAuthFilter filter = novoFiltro();
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
  }

  @Test
  @DisplayName("Deve autenticar com sessao opaca quando JWT estiver ausente")
  void deveAutenticarComSessaoOpacaQuandoJwtEstiverAusente() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    request.setCookies(new Cookie("SC_SESSION", "sessao-segura"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(sessaoUsuarioService.validarSessao("sessao-segura")).thenReturn(usuarioId);
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserById(usuarioId);
  }

  private JwtAuthFilter novoFiltro() {
    return new JwtAuthFilter(
        jwtService, userDetailsService, sessaoUsuarioService, requestFingerprintService);
  }
}
