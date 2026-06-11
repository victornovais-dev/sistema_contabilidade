package com.sistema_contabilidade.auth.config;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

@DisplayName("AuthProviderStartupValidator unit tests")
class AuthProviderStartupValidatorTest {

  @Test
  @DisplayName("Deve bloquear provider local no profile prod")
  void deveBloquearProviderLocalNoProfileProd() {
    AuthProviderProperties authProviderProperties = new AuthProviderProperties();
    authProviderProperties.setProvider(AuthProvider.LOCAL);
    CognitoProperties cognitoProperties = new CognitoProperties();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");

    AuthProviderStartupValidator validator =
        new AuthProviderStartupValidator(authProviderProperties, cognitoProperties, environment);

    assertThrows(IllegalStateException.class, () -> invokeValidate(validator));
  }

  @Test
  @DisplayName("Deve exigir configuracao minima no provider Cognito em producao")
  void deveExigirConfiguracaoMinimaNoProviderCognitoEmProducao() {
    AuthProviderProperties authProviderProperties = new AuthProviderProperties();
    authProviderProperties.setProvider(AuthProvider.COGNITO);
    CognitoProperties cognitoProperties = new CognitoProperties();
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");

    AuthProviderStartupValidator validator =
        new AuthProviderStartupValidator(authProviderProperties, cognitoProperties, environment);

    assertThrows(IllegalStateException.class, () -> invokeValidate(validator));
  }

  @Test
  @DisplayName("Deve aceitar provider Cognito em prod quando configuracao estiver completa")
  void deveAceitarProviderCognitoEmProdQuandoConfiguracaoEstiverCompleta() {
    AuthProviderProperties authProviderProperties = new AuthProviderProperties();
    authProviderProperties.setProvider(AuthProvider.COGNITO);
    CognitoProperties cognitoProperties = new CognitoProperties();
    cognitoProperties.setUserPoolId("pool");
    cognitoProperties.setAppClientId("client");
    MockEnvironment environment = new MockEnvironment();
    environment.setActiveProfiles("prod");
    environment.setProperty("aws.region", "us-east-1");
    environment.setProperty("app.session.crypto-secret", "0123456789ABCDEF0123456789ABCDEF");
    environment.setProperty("app.jwt.ec-private-key", "priv");
    environment.setProperty("app.jwt.ec-public-key", "pub");

    AuthProviderStartupValidator validator =
        new AuthProviderStartupValidator(authProviderProperties, cognitoProperties, environment);

    assertDoesNotThrow(() -> invokeValidate(validator));
  }

  private void invokeValidate(AuthProviderStartupValidator validator) {
    org.springframework.test.util.ReflectionTestUtils.invokeMethod(validator, "validate");
  }
}
