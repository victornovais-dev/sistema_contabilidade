package com.sistema_contabilidade.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.service.AuthService;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.DefaultCsrfToken;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthController unit tests")
class AuthControllerTest {

  @Mock private AuthService authService;

  @InjectMocks private AuthController authController;

  @Test
  @DisplayName("Deve delegar login para service")
  void loginDeveDelegarParaService() {
    // Arrange
    LoginRequest request = new LoginRequest("ana@email.com", "123456");
    JwtLoginResponse response = new JwtLoginResponse("jwt-token", "Bearer");
    when(authService.login(request)).thenReturn(response);

    // Act
    ResponseEntity<JwtLoginResponse> result = authController.login(request);

    // Assert
    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("jwt-token", result.getBody().accessToken());
    verify(authService).login(request);
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
  @DisplayName("Deve retornar token CSRF no endpoint csrf")
  void csrfDeveRetornarToken() {
    CsrfToken csrfToken = new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "token-123");

    ResponseEntity<Map<String, String>> result = authController.csrf(csrfToken);

    assertEquals(HttpStatus.OK, result.getStatusCode());
    assertEquals("token-123", result.getBody().get("token"));
  }
}
