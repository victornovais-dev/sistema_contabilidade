package com.sistema_contabilidade.security.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.UUID;
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

  @Test
  @DisplayName("Deve incluir fingerprint do dispositivo quando informado")
  void deveIncluirFingerprintDoDispositivoQuandoInformado() throws Exception {
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

    String token = jwtService.generateToken(userDetails, "fingerprint");

    assertEquals("fingerprint", jwtService.extractDeviceFingerprint(token));
    assertTrue(jwtService.isTokenValid(token, userDetails, "fingerprint"));
  }

  @Test
  @DisplayName("Deve incluir userId e cognitoSub quando informados")
  void deveIncluirUserIdECognitoSubQuandoInformados() throws Exception {
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
    UUID userId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    String token = jwtService.generateToken(userDetails, userId, "sub-123", "fingerprint");

    assertEquals(userId, jwtService.extractUserId(token));
    assertEquals("sub-123", jwtService.extractCognitoSub(token));
  }
}
