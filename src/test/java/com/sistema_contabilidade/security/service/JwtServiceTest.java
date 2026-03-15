package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("JwtService unit tests")
class JwtServiceTest {

  @Test
  @DisplayName("Deve gerar e validar token jwt")
  void deveGerarEValidarToken() throws Exception {
    // Arrange
    JwtService jwtService = new JwtService();
    KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
    keyPairGenerator.initialize(256);
    KeyPair keyPair = keyPairGenerator.generateKeyPair();
    String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
    String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
    ReflectionTestUtils.setField(jwtService, "ecPrivateKey", privateKey);
    ReflectionTestUtils.setField(jwtService, "ecPublicKey", publicKey);
    ReflectionTestUtils.setField(jwtService, "expirationMinutes", 60L);
    ReflectionTestUtils.invokeMethod(jwtService, "initializeKeys");
    var userDetails =
        User.withUsername("user@email.com").password("x").authorities("ROLE_USER").build();

    // Act
    String token = jwtService.generateToken(userDetails);

    // Assert
    assertEquals("user@email.com", jwtService.extractUsername(token));
    assertTrue(jwtService.isTokenValid(token, userDetails));
  }
}
