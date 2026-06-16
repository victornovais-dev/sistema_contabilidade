package com.sistema_contabilidade.auth.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.HashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CognitoSecretHashService unit tests")
class CognitoSecretHashServiceTest {

  @Test
  @DisplayName("Deve ignorar SECRET_HASH quando client secret nao estiver configurado")
  void deveIgnorarSecretHashQuandoClientSecretNaoEstiverConfigurado() {
    CognitoProperties properties = new CognitoProperties();
    properties.setAppClientId("client-id");
    CognitoSecretHashService service = new CognitoSecretHashService(properties);
    Map<String, String> params = new HashMap<>();

    service.addSecretHashIfNeeded(params, "ana@email.com");

    assertFalse(params.containsKey("SECRET_HASH"));
  }

  @Test
  @DisplayName("Deve calcular e adicionar SECRET_HASH quando client secret estiver configurado")
  void deveCalcularEAdicionarSecretHashQuandoClientSecretEstiverConfigurado() throws Exception {
    CognitoProperties properties = new CognitoProperties();
    properties.setAppClientId("client-id");
    properties.setAppClientSecret("secret-value");
    CognitoSecretHashService service = new CognitoSecretHashService(properties);
    Map<String, String> params = new HashMap<>();

    service.addSecretHashIfNeeded(params, "ana@email.com");

    Mac mac = Mac.getInstance("HmacSHA256");
    mac.init(new SecretKeySpec("secret-value".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
    mac.update("ana@email.com".getBytes(StandardCharsets.UTF_8));
    String expected =
        Base64.getEncoder()
            .encodeToString(mac.doFinal("client-id".getBytes(StandardCharsets.UTF_8)));

    assertEquals(expected, params.get("SECRET_HASH"));
    assertTrue(properties.hasClientSecret());
  }
}
