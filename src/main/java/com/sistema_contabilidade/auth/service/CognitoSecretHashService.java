package com.sistema_contabilidade.auth.service;

import com.sistema_contabilidade.auth.config.CognitoProperties;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@ConditionalOnProperty(name = "app.auth.provider", havingValue = "cognito")
public class CognitoSecretHashService {

  private final CognitoProperties cognitoProperties;

  public void addSecretHashIfNeeded(Map<String, String> params, String username) {
    if (!cognitoProperties.hasClientSecret()) {
      return;
    }
    params.put("SECRET_HASH", secretHash(username));
  }

  public String secretHash(String username) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(
          new SecretKeySpec(
              cognitoProperties.getAppClientSecret().getBytes(StandardCharsets.UTF_8),
              "HmacSHA256"));
      mac.update(username.getBytes(StandardCharsets.UTF_8));
      byte[] raw = mac.doFinal(cognitoProperties.getAppClientId().getBytes(StandardCharsets.UTF_8));
      return Base64.getEncoder().encodeToString(raw);
    } catch (Exception exception) {
      throw new IllegalStateException("Falha ao calcular SECRET_HASH do Cognito", exception);
    }
  }
}
