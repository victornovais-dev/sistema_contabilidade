package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtService unit tests")
class JwtServiceTest {

  @Test
  @DisplayName("Deve gerar e validar token jwt")
  void deveGerarEValidarToken() {
    // Arrange
    JwtService jwtService = new JwtService();
    ReflectionTestUtils.setField(jwtService, "jwtSecret", "0123456789ABCDEF0123456789ABCDEF");
    ReflectionTestUtils.setField(jwtService, "expirationMinutes", 60L);
    ReflectionTestUtils.invokeMethod(jwtService, "validateSecret");
    var userDetails =
        User.withUsername("user@email.com").password("x").authorities("ROLE_USER").build();

    // Act
    String token = jwtService.generateToken(userDetails);

    // Assert
    assertEquals("user@email.com", jwtService.extractUsername(token));
    assertTrue(jwtService.isTokenValid(token, userDetails));
  }
}
