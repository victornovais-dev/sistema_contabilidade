package com.sistema_contabilidade.security.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@DisplayName("InputSanitizer unit tests")
class InputSanitizerTest {

  private final InputSanitizer inputSanitizer = new InputSanitizer();

  @Test
  @DisplayName("Deve normalizar espacos e email")
  void deveNormalizarEspacosEEmail() {
    assertEquals("Ana Maria", inputSanitizer.sanitizeInlineText("  Ana   Maria  ", "nome", 120));
    assertEquals("ana@email.com", inputSanitizer.sanitizeEmail(" ANA@EMAIL.COM ", "email"));
  }

  @Test
  @DisplayName("Deve bloquear payload tipico de XSS")
  void deveBloquearPayloadTipicoDeXss() {
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> inputSanitizer.sanitizeInlineText("<script>alert(1)</script>", "nome", 120));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }

  @Test
  @DisplayName("Deve bloquear payload tipico de SQL injection")
  void deveBloquearPayloadTipicoDeSqlInjection() {
    ResponseStatusException exception =
        assertThrows(
            ResponseStatusException.class,
            () -> inputSanitizer.sanitizeInlineText("' OR '1'='1", "descricao", 120));

    assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
  }
}
