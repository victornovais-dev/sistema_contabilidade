package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("SessionCipherService unit tests")
class SessionCipherServiceTest {

  @Test
  @DisplayName("Deve criptografar e descriptografar o id da sessao")
  void deveCriptografarEDescriptografar() {
    // Arrange
    SessionCipherService service = new SessionCipherService();
    ReflectionTestUtils.setField(service, "cryptoSecret", "0123456789ABCDEF0123456789ABCDEF");
    ReflectionTestUtils.invokeMethod(service, "validateSecret");
    UUID sessaoId = UUID.fromString("11111111-1111-1111-1111-111111111111");

    // Act
    String token = service.encrypt(sessaoId);
    UUID decifrado = service.decrypt(token);

    // Assert
    assertNotNull(token);
    assertEquals(sessaoId, decifrado);
  }

  @Test
  @DisplayName("Deve lancar unauthorized para token invalido")
  void deveLancarUnauthorizedParaTokenInvalido() {
    // Arrange
    SessionCipherService service = new SessionCipherService();
    ReflectionTestUtils.setField(service, "cryptoSecret", "0123456789ABCDEF0123456789ABCDEF");
    ReflectionTestUtils.invokeMethod(service, "validateSecret");

    // Act / Assert
    assertThrows(ResponseStatusException.class, () -> service.decrypt("token-invalido"));
  }
}
