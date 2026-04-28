package com.sistema_contabilidade.security.validation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Method;
import java.util.Set;
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

  @Test
  @DisplayName("Deve normalizar texto multilinea e conjunto de valores")
  void deveNormalizarTextoMultilineaEConjuntoDeValores() {
    String multilinea =
        inputSanitizer.sanitizeMultilineText(" linha 1 \r\nlinha 2\t", "observacao", 120);
    Set<String> roles =
        inputSanitizer.sanitizeInlineTextSet(Set.of(" admin ", "ADMIN", " "), "role", 20);

    assertEquals("linha 1 \nlinha 2", multilinea);
    assertEquals(Set.of("admin", "ADMIN"), roles);
  }

  @Test
  @DisplayName("Deve aceitar email nulo e conjunto vazio")
  void deveAceitarEmailNuloEConjuntoVazio() {
    assertNull(inputSanitizer.sanitizeEmail(null, "email"));
    assertEquals(Set.of(), inputSanitizer.sanitizeInlineTextSet(null, "role", 20));
    assertEquals(Set.of(), inputSanitizer.sanitizeInlineTextSet(Set.of(), "role", 20));
  }

  @Test
  @DisplayName("Deve bloquear payload codificado e campo acima do limite")
  void deveBloquearPayloadCodificadoECampoAcimaDoLimite() {
    ResponseStatusException xssException =
        assertThrows(
            ResponseStatusException.class,
            () -> inputSanitizer.sanitizeInlineText("%3Cscript%3Eboom%3C/script%3E", "nome", 120));
    ResponseStatusException limiteException =
        assertThrows(
            ResponseStatusException.class,
            () -> inputSanitizer.sanitizeInlineText("abcdef", "nome", 3));

    assertEquals(HttpStatus.BAD_REQUEST, xssException.getStatusCode());
    assertEquals(HttpStatus.BAD_REQUEST, limiteException.getStatusCode());
    assertTrue(limiteException.getReason().contains("Input invalido"));
  }

  @Test
  @DisplayName("Deve decodificar multiplas camadas e tolerar sequencia invalida")
  void deveDecodificarMultiplasCamadasETolerarSequenciaInvalida() {
    ResponseStatusException tripleEncodedException =
        assertThrows(
            ResponseStatusException.class,
            () ->
                inputSanitizer.sanitizeInlineText(
                    "%25253Cscript%25253Eboom%25253C/script%25253E", "nome", 120));
    String literalPercent = inputSanitizer.sanitizeInlineText("%", "nome", 10);

    assertEquals(HttpStatus.BAD_REQUEST, tripleEncodedException.getStatusCode());
    assertEquals("%", literalPercent);
  }

  @Test
  @DisplayName("Deve limitar preview privado")
  void deveLimitarPreviewPrivado() throws Exception {
    Method preview = InputSanitizer.class.getDeclaredMethod("preview", String.class);
    preview.setAccessible(true);

    String vazio = (String) preview.invoke(inputSanitizer, new Object[] {null});
    String longo = (String) preview.invoke(inputSanitizer, "a".repeat(150));

    assertEquals("", vazio);
    assertEquals(120, longo.length());
  }

  @Test
  @DisplayName("Deve cobrir helpers privados de deteccao de tags")
  void deveCobrirHelpersPrivadosDeDeteccaoDeTags() throws Exception {
    Method containsHtmlTag =
        InputSanitizer.class.getDeclaredMethod("containsHtmlTag", String.class);
    Method normalizeTagStart =
        InputSanitizer.class.getDeclaredMethod("normalizeTagStart", String.class, int.class);
    Method skipWhitespace =
        InputSanitizer.class.getDeclaredMethod("skipWhitespace", String.class, int.class);
    Method isValidTagStart =
        InputSanitizer.class.getDeclaredMethod("isValidTagStart", String.class, int.class);

    containsHtmlTag.setAccessible(true);
    normalizeTagStart.setAccessible(true);
    skipWhitespace.setAccessible(true);
    isValidTagStart.setAccessible(true);

    boolean hasSlashTag =
        (boolean) containsHtmlTag.invoke(inputSanitizer, "prefix < /div > suffix");
    boolean hasInvalidTag =
        (boolean) containsHtmlTag.invoke(inputSanitizer, "valor < 123 > literal");
    int normalizedStart = (int) normalizeTagStart.invoke(inputSanitizer, "< /div >", 1);
    int skippedStart = (int) skipWhitespace.invoke(inputSanitizer, "   abc", 0);
    boolean validOutOfRange = (boolean) isValidTagStart.invoke(inputSanitizer, "", 0);

    assertTrue(hasSlashTag);
    assertFalse(hasInvalidTag);
    assertEquals(3, normalizedStart);
    assertEquals(3, skippedStart);
    assertFalse(validOutOfRange);
  }
}
