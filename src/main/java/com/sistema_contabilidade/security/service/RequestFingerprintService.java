package com.sistema_contabilidade.security.service;

import jakarta.servlet.http.HttpServletRequest;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class RequestFingerprintService {

  private static final List<String> FINGERPRINT_HEADERS =
      List.of(
          "User-Agent", "Accept-Language", "Sec-CH-UA", "Sec-CH-UA-Platform", "Sec-CH-UA-Mobile");

  public String generateFingerprint(HttpServletRequest request) {
    String rawFingerprint =
        FINGERPRINT_HEADERS.stream()
            .map(request::getHeader)
            .filter(Objects::nonNull)
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .collect(Collectors.joining("|"));
    if (rawFingerprint.isBlank()) {
      rawFingerprint = "unknown-device";
    }
    return sha256Base64Url(rawFingerprint);
  }

  public String resolveClientIp(HttpServletRequest request) {
    String forwardedFor = request.getHeader("X-Forwarded-For");
    if (forwardedFor != null && !forwardedFor.isBlank()) {
      return forwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }

  private String sha256Base64Url(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("Falha ao calcular fingerprint do dispositivo", exception);
    }
  }
}
