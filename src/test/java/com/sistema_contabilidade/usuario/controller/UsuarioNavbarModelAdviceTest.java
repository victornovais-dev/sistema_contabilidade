package com.sistema_contabilidade.usuario.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;

@DisplayName("UsuarioNavbarModelAdvice unit tests")
class UsuarioNavbarModelAdviceTest {

  private final UsuarioNavbarModelAdvice advice = new UsuarioNavbarModelAdvice();

  @Test
  @DisplayName("Deve definir tema antes da renderizacao para contas autorizadas")
  void accountThemeDeveRetornarTemaParaContaAutorizada() {
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    org.mockito.Mockito.when(authentication.getName()).thenReturn("lucas@clarigest.com");

    assertEquals("victor-campaign", advice.accountTheme(authentication));
  }

  @Test
  @DisplayName("Deve preservar tema padrao para outras contas")
  void accountThemeDeveRetornarNuloParaOutraConta() {
    Authentication authentication = org.mockito.Mockito.mock(Authentication.class);
    org.mockito.Mockito.when(authentication.getName()).thenReturn("ana@email.com");

    assertNull(advice.accountTheme(authentication));
  }
}
