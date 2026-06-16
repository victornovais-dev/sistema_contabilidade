package com.sistema_contabilidade.auth.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.cognitoidentityprovider.CognitoIdentityProviderClient;

@DisplayName("AwsCognitoConfig unit tests")
class AwsCognitoConfigTest {

  @Test
  @DisplayName("Deve criar client Cognito com region informada")
  void deveCriarClientCognitoComRegionInformada() {
    AwsCognitoConfig config = new AwsCognitoConfig();

    CognitoIdentityProviderClient client = config.cognitoIdentityProviderClient("us-east-1");
    try {
      assertNotNull(client);
      assertEquals("us-east-1", client.serviceClientConfiguration().region().id());
    } finally {
      client.close();
    }
  }
}
