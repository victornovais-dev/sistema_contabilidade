package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.stream.Stream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.security.core.Authentication;

@DisplayName("UsuarioNavbarModelAdvice unit tests")
class UsuarioNavbarModelAdviceTest {

  private final UsuarioNavbarModelAdvice advice = new UsuarioNavbarModelAdvice();

  @ParameterizedTest(name = "{0} deve receber o tema {1}")
  @MethodSource("contasComTemaPersonalizado")
  @DisplayName("Deve definir tema personalizado para contas autorizadas")
  void accountThemeDeveRetornarTemaPersonalizado(String email, String temaEsperado) {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn(email);

    assertEquals(temaEsperado, advice.accountTheme(authentication));
  }

  private static Stream<Arguments> contasComTemaPersonalizado() {
    return Stream.of(
        Arguments.of("victornovais77@gmail.com", "andre-do-prado"),
        Arguments.of("lucas@clarigest.com", "marco-feliciano"),
        Arguments.of("marcofeliciano@sacs.com", "marco-feliciano"));
  }

  @Test
  @DisplayName("Deve preservar tema padrao para outras contas")
  void accountThemeDeveRetornarNuloParaOutraConta() {
    Authentication authentication = mock(Authentication.class);
    when(authentication.getName()).thenReturn("ana@email.com");

    assertNull(advice.accountTheme(authentication));
  }
}
