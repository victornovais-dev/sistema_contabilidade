package com.sistema_contabilidade.auth.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.dto.JwtLoginResponse;
import com.sistema_contabilidade.auth.dto.LoginRequest;
import com.sistema_contabilidade.auth.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

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
}
