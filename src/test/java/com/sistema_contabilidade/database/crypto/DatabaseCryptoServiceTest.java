package com.sistema_contabilidade.database.crypto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.database.crypto.service.DatabaseCryptoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

@DisplayName("DatabaseCryptoService unit tests")
class DatabaseCryptoServiceTest {

  private DatabaseCryptoService cryptoService;

  @BeforeEach
  void setUp() {
    cryptoService = new DatabaseCryptoService("0123456789ABCDEF0123456789ABCDEF");
    ReflectionTestUtils.invokeMethod(cryptoService, "initializeKeys");
  }

  @Test
  @DisplayName("Deve usar AES-GCM aleatorio e recuperar texto original")
  void deveCriptografarComNonceAleatorio() {
    String first = cryptoService.encrypt("dado sensivel");
    String second = cryptoService.encrypt("dado sensivel");

    assertTrue(first.startsWith("enc:v1:"));
    assertNotEquals(first, second);
    assertEquals("dado sensivel", cryptoService.decrypt(first));
    assertEquals("dado sensivel", cryptoService.decrypt(second));
  }

  @Test
  @DisplayName("Deve rejeitar payload adulterado")
  void deveRejeitarPayloadAdulterado() {
    String ciphertext = cryptoService.encrypt("dado sensivel");
    char replacement = ciphertext.endsWith("A") ? 'B' : 'A';
    String tampered = ciphertext.substring(0, ciphertext.length() - 1) + replacement;

    assertThrows(DatabaseCryptoException.class, () -> cryptoService.decrypt(tampered));
  }

  @Test
  @DisplayName("Deve aceitar texto legado durante migracao")
  void deveAceitarTextoLegado() {
    assertEquals("texto legado", cryptoService.decrypt("texto legado"));
  }

  @Test
  @DisplayName("Deve separar blind indexes por contexto")
  void deveSepararBlindIndexesPorContexto() {
    String first = cryptoService.blindIndex("usuario.email", "teste@email.com");
    String repeated = cryptoService.blindIndex("usuario.email", "teste@email.com");
    String otherContext = cryptoService.blindIndex("item.email", "teste@email.com");

    assertEquals(first, repeated);
    assertNotEquals(first, otherContext);
    assertEquals(64, first.length());
  }
}
