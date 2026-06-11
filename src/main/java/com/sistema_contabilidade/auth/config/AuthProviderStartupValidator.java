package com.sistema_contabilidade.auth.config;

import java.util.Arrays;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AuthProviderStartupValidator {

  private final AuthProviderProperties authProviderProperties;
  private final CognitoProperties cognitoProperties;
  private final Environment environment;

  @jakarta.annotation.PostConstruct
  void validate() {
    List<String> activeProfiles = Arrays.asList(environment.getActiveProfiles());
    boolean prodProfile = activeProfiles.contains("prod");

    if (prodProfile
        && authProviderProperties.getProvider() == AuthProvider.LOCAL
        && !authProviderProperties.isAllowLocalInProd()) {
      throw new IllegalStateException(
          "app.auth.provider=local nao e permitido no profile prod sem override explicito");
    }

    if (!prodProfile || authProviderProperties.getProvider() != AuthProvider.COGNITO) {
      return;
    }

    requireValue(environment.getProperty("aws.region"), "AWS_REGION");
    requireValue(cognitoProperties.getUserPoolId(), "COGNITO_USER_POOL_ID");
    requireValue(cognitoProperties.getAppClientId(), "COGNITO_APP_CLIENT_ID");
    requireValue(environment.getProperty("app.session.crypto-secret"), "SESSION_CRYPTO_SECRET");
    requireValue(environment.getProperty("app.jwt.ec-private-key"), "JWT_EC_PRIVATE_KEY");
    requireValue(environment.getProperty("app.jwt.ec-public-key"), "JWT_EC_PUBLIC_KEY");
  }

  private void requireValue(String value, String envName) {
    if (value == null || value.isBlank()) {
      throw new IllegalStateException("Configuracao obrigatoria ausente para Cognito: " + envName);
    }
  }
}
