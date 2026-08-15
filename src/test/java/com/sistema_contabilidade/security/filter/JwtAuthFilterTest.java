package com.sistema_contabilidade.security.filter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.model.SessaoUsuario;
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
    assertNull(request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
  }

  @Test
  @DisplayName("Deve autenticar usando userId do JWT quando claim estiver presente")
  void deveAutenticarUsandoUserIdDoJwtQuandoClaimEstiverPresente() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("Authorization", "Bearer token-valido");
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUserId("token-valido")).thenReturn(usuarioId);
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails, "fingerprint")).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    verify(userDetailsService).loadUserById(usuarioId);
    verify(userDetailsService, never()).loadUserByUsername(anyString());
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
  @DisplayName("Deve ignorar token legado invalido quando outro cookie e valido")
  void deveIgnorarTokenLegadoInvalidoQuandoOutroCookieEValido() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/criar_usuario");
    request.setCookies(
        new Cookie("SC_TOKEN", "token-antigo"), new Cookie("SC_TOKEN", "token-valido"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    var userDetails =
        User.withUsername("admin@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(jwtService.extractUserId("token-antigo"))
        .thenThrow(new ExpiredJwtException(null, null, "Token expirado"));
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUsername("token-valido")).thenReturn("admin@email.com");
    when(userDetailsService.loadUserByUsername("admin@email.com")).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails, "fingerprint")).thenReturn(true);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertNull(response.getCookie("SC_TOKEN"));
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
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");

    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(sessaoUsuarioService.obterSessaoAtiva("sessao-segura"))
        .thenReturn(sessao(sessaoId, usuarioId));
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(sessaoId, request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
    assertEquals(
        "sessao-segura",
        request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_TOKEN_ATTRIBUTE));
    verify(userDetailsService).loadUserById(usuarioId);
  }

  @Test
  @DisplayName("Deve ignorar cookie de sessao antigo quando outro cookie e valido")
  void deveIgnorarCookieDeSessaoAntigoQuandoOutroCookieEValido() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    request.setCookies(
        new Cookie("SC_SESSION", "sessao-antiga"),
        new Cookie("SC_SESSION", "sessao-valida"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(sessaoUsuarioService.obterSessaoAtiva("sessao-antiga"))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Sessao invalida"));
    when(sessaoUsuarioService.obterSessaoAtiva("sessao-valida"))
        .thenReturn(sessao(sessaoId, usuarioId));
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertEquals(sessaoId, request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
    assertEquals(
        "sessao-valida",
        request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_TOKEN_ATTRIBUTE));
    assertNull(response.getCookie("SC_SESSION"));
  }

  @Test
  @DisplayName("Deve publicar sessao validada quando Bearer autentica primeiro")
  void devePublicarSessaoValidadaQuandoBearerAutenticaPrimeiro() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    request.addHeader("Authorization", "Bearer token-valido");
    request.setCookies(new Cookie("SC_SESSION", "sessao-segura"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    UUID sessaoId = UUID.fromString("22222222-2222-2222-2222-222222222222");
    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUserId("token-valido")).thenReturn(usuarioId);
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails, "fingerprint")).thenReturn(true);
    when(sessaoUsuarioService.obterSessaoAtiva("sessao-segura"))
        .thenReturn(sessao(sessaoId, usuarioId));

    filter.doFilter(request, response, chain);

    assertEquals(sessaoId, request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
    verify(userDetailsService, times(1)).loadUserById(usuarioId);
  }

  @Test
  @DisplayName("Deve manter Bearer autenticado sem publicar sessao invalida")
  void deveManterBearerAutenticadoSemPublicarSessaoInvalida() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/itens");
    request.addHeader("Authorization", "Bearer token-valido");
    request.setCookies(new Cookie("SC_SESSION", "sessao-invalida"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();
    UUID usuarioId = UUID.fromString("11111111-1111-1111-1111-111111111111");
    var userDetails =
        User.withUsername("ana@email.com").password("hash").authorities("ROLE_ADMIN").build();
    when(requestFingerprintService.generateFingerprint(request)).thenReturn("fingerprint");
    when(jwtService.extractUserId("token-valido")).thenReturn(usuarioId);
    when(userDetailsService.loadUserById(usuarioId)).thenReturn(userDetails);
    when(jwtService.isTokenValid("token-valido", userDetails, "fingerprint")).thenReturn(true);
    when(sessaoUsuarioService.obterSessaoAtiva("sessao-invalida"))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Sessao invalida"));

    filter.doFilter(request, response, chain);

    assertNotNull(SecurityContextHolder.getContext().getAuthentication());
    assertNull(request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
    assertNull(request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_TOKEN_ATTRIBUTE));
    assertEquals(0, response.getCookie("SC_SESSION").getMaxAge());
  }

  @Test
  @DisplayName("Deve limpar cookie de sessao invalida")
  void deveLimparCookieDeSessaoInvalida() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/home");
    request.setCookies(new Cookie("SC_SESSION", "sessao-invalida"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    when(sessaoUsuarioService.obterSessaoAtiva("sessao-invalida"))
        .thenThrow(
            new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.UNAUTHORIZED, "Sessao invalida"));

    filter.doFilter(request, response, chain);

    assertNull(SecurityContextHolder.getContext().getAuthentication());
    assertNull(request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_ID_ATTRIBUTE));
    assertNull(request.getAttribute(JwtAuthFilter.VALIDATED_SESSION_TOKEN_ATTRIBUTE));
    Cookie cookieLimpo = response.getCookie("SC_SESSION");
    assertNotNull(cookieLimpo);
    assertEquals(0, cookieLimpo.getMaxAge());
    assertEquals("/", cookieLimpo.getPath());
  }

  @Test
  @DisplayName("Deve marcar cookie legado como seguro quando request vier por proxy HTTPS")
  void deveMarcarCookieLegadoComoSeguroQuandoRequestVierPorProxyHttps() throws Exception {
    JwtAuthFilter filter = novoFiltro();
    MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/usuarios");
    request.addHeader("X-Forwarded-Proto", "https");
    request.setCookies(new Cookie("SC_TOKEN", "token-expirado"));
    MockHttpServletResponse response = new MockHttpServletResponse();
    MockFilterChain chain = new MockFilterChain();

    when(jwtService.extractUsername("token-expirado"))
        .thenThrow(new ExpiredJwtException(null, null, "Token expirado"));

    filter.doFilter(request, response, chain);

    Cookie cookieLimpo = response.getCookie("SC_TOKEN");
    assertNotNull(cookieLimpo);
    assertTrue(cookieLimpo.getSecure());
  }

  private JwtAuthFilter novoFiltro() {
    return new JwtAuthFilter(
        jwtService, userDetailsService, sessaoUsuarioService, requestFingerprintService);
  }

  private SessaoUsuario sessao(UUID sessaoId, UUID usuarioId) {
    SessaoUsuario sessao = new SessaoUsuario();
    sessao.setId(sessaoId);
    sessao.setUsuarioId(usuarioId);
    return sessao;
  }
}
