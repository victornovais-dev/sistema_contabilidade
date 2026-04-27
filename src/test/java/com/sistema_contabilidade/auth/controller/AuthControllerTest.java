package com.sistema_contabilidade.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.AuthenticatedLoginResult;
import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.service.AuthService;
import com.sistema_contabilidade.security.service.AdminRouteService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController unit tests")
class AuthControllerTest {

  @Mock private AuthService authService;
  @Mock private AdminRouteService adminRouteService;

  @InjectMocks private AuthController authController;

  @Test
  @DisplayName("Deve delegar login para service e anexar cookie de sessao")
  void loginDeveDelegarParaService() {
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    AuthenticatedLoginResult response =
        new AuthenticatedLoginResult(new JwtLoginResponse("jwt-token", "Bearer"), "sessao-token");
    MockHttpServletRequest httpRequest = new MockHttpServletRequest("POST", "/api/v1/auth/login");
    MockHttpServletResponse httpResponse = new MockHttpServletResponse();
    ReflectionTestUtils.setField(authController, "sessionCookieName", "SC_SESSION");
    ReflectionTestUtils.setField(authController, "sessionTtlMinutes", 480L);
    when(authService.login(request, httpRequest)).thenReturn(response);

    ResponseEntity<JwtLoginResponse> result =
        authController.login(request, httpRequest, httpResponse);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("jwt-token", result.getBody().accessToken());
    assertTrue(
        httpResponse.getHeaders(HttpHeaders.SET_COOKIE).stream()
            .anyMatch(h -> h.contains("SC_SESSION=sessao-token")));
    verify(authService).login(request, httpRequest);
  }

  @Test
  @DisplayName("Deve retornar nome do usuario autenticado no endpoint me")
  void meDeveRetornarNomeDoUsuario() {
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    when(authentication.getName()).thenReturn("victor@email.com");

    ResponseEntity<String> result = authController.me(authentication);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("victor@email.com", result.getBody());
  }

  @Test
  @DisplayName("Deve retornar roles do usuario autenticado no endpoint meRoles")
  void meRolesDeveRetornarRoles() {
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    doReturn(
            List.of(
                new SimpleGrantedAuthority("ROLE_ADMIN"),
                new SimpleGrantedAuthority("ROLE_SUPPORT"),
                new SimpleGrantedAuthority("ITEM_CREATE")))
        .when(authentication)
        .getAuthorities();

    ResponseEntity<List<String>> result = authController.meRoles(authentication);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals(List.of("ADMIN", "SUPPORT"), result.getBody());
  }

  @Test
  @DisplayName("Deve delegar refresh para service usando cookie de sessao")
  void refreshDeveDelegarParaService() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/refresh");
    request.setCookies(new jakarta.servlet.http.Cookie("SC_SESSION", "sessao-token"));
    ReflectionTestUtils.setField(authController, "sessionCookieName", "SC_SESSION");
    when(authService.refresh("sessao-token", request))
        .thenReturn(new JwtLoginResponse("novo-token", "Bearer"));

    ResponseEntity<JwtLoginResponse> result = authController.refresh(request);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("novo-token", result.getBody().accessToken());
  }

  @Test
  @DisplayName("Deve delegar logout para service e limpar cookie de sessao")
  void logoutDeveLimparCookieDeSessao() {
    MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/auth/logout");
    MockHttpServletResponse response = new MockHttpServletResponse();
    request.setCookies(new jakarta.servlet.http.Cookie("SC_SESSION", "sessao-token"));
    ReflectionTestUtils.setField(authController, "sessionCookieName", "SC_SESSION");

    ResponseEntity<Void> result = authController.logout(request, response);

    assertEquals(HttpStatus.NO_CONTENT, result.getStatusCode());
    assertTrue(
        response.getHeaders(HttpHeaders.SET_COOKIE).stream()
            .anyMatch(h -> h.contains("SC_SESSION=")));
    verify(authService).logout("sessao-token");
  }

  @Test
  @DisplayName("Deve retornar token CSRF no endpoint csrf")
  void csrfDeveRetornarToken() {
    CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-123");

    ResponseEntity<Map<String, String>> result = authController.csrf(csrfToken);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("token-123", result.getBody().get("token"));
  }

  @Test
  @DisplayName("Deve retornar rotas admin somente para perfil admin")
  void routesDeveRetornarRotasAdmin() {
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    doReturn(List.of(new SimpleGrantedAuthority("ROLE_ADMIN")))
        .when(authentication)
        .getAuthorities();
    when(adminRouteService.routeConfig()).thenReturn(Map.of("adminPagePath", "/segredo/admin"));

    ResponseEntity<Map<String, String>> result = authController.routes(authentication);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("/segredo/admin", result.getBody().get("adminPagePath"));
  }
}
