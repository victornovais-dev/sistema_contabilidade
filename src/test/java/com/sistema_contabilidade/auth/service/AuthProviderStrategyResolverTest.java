package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.sistema_contabilidade.auth.config.AuthProvider;
import com.sistema_contabilidade.auth.config.AuthProviderProperties;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("AuthProviderStrategyResolver unit tests")
class AuthProviderStrategyResolverTest {

  @Test
  @DisplayName("Deve resolver estrategia do provider atual")
  void deveResolverEstrategiaDoProviderAtual() {
    AuthProviderProperties properties = new AuthProviderProperties();
    properties.setProvider(AuthProvider.COGNITO);
    AuthProviderStrategy strategy = mock(AuthProviderStrategy.class);
    when(strategy.supports(AuthProvider.COGNITO)).thenReturn(true);

    AuthProviderStrategyResolver resolver =
        new AuthProviderStrategyResolver(List.of(strategy), properties);

    assertEquals(strategy, resolver.current());
    assertEquals(AuthProvider.COGNITO, resolver.currentProvider());
  }

  @Test
  @DisplayName("Deve falhar quando nao existir estrategia para o provider")
  void deveFalharQuandoNaoExistirEstrategiaParaOProvider() {
    AuthProviderProperties properties = new AuthProviderProperties();
    properties.setProvider(AuthProvider.LOCAL);
    AuthProviderStrategy strategy = mock(AuthProviderStrategy.class);
    when(strategy.supports(AuthProvider.LOCAL)).thenReturn(false);

    AuthProviderStrategyResolver resolver =
        new AuthProviderStrategyResolver(List.of(strategy), properties);

    assertThrows(IllegalStateException.class, resolver::current);
  }
}
