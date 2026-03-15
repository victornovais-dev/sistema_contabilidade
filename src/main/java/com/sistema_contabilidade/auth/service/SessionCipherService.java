package com.sistema_contabilidade.auth.service;

import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Arrays;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class SessionCipherService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int KEY_SIZE_BYTES = 32;
  private static final int IV_SIZE = 12;
  private static final int TAG_BITS = 128;

  private final SecureRandom secureRandom = new SecureRandom();

  @Value("${app.session.crypto-secret}")
  private String cryptoSecret;

  @PostConstruct
  void validateSecret() {
    int keyLength = cryptoSecret.getBytes(StandardCharsets.UTF_8).length;
    if (keyLength != KEY_SIZE_BYTES) {
      throw new IllegalStateException("app.session.crypto-secret precisa ter 32 bytes");
    }
  }

  public String encrypt(UUID sessionId) {
    try {
      byte[] iv = new byte[IV_SIZE];
      secureRandom.nextBytes(iv);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
      byte[] encrypted = cipher.doFinal(sessionId.toString().getBytes(StandardCharsets.UTF_8));

      byte[] payload =
          ByteBuffer.allocate(iv.length + encrypted.length).put(iv).put(encrypted).array();

      return Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (Exception ex) {
      throw new ResponseStatusException(
          HttpStatus.INTERNAL_SERVER_ERROR, "Falha ao criptografar sessao", ex);
    }
  }

  public UUID decrypt(String token) {
    try {
      byte[] payload = Base64.getUrlDecoder().decode(token);
      if (payload.length <= IV_SIZE) {
        throw new IllegalArgumentException("Token invalido");
      }

      byte[] iv = Arrays.copyOfRange(payload, 0, IV_SIZE);
      byte[] encrypted = Arrays.copyOfRange(payload, IV_SIZE, payload.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, keySpec(), new GCMParameterSpec(TAG_BITS, iv));
      String decrypted = new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
      return UUID.fromString(decrypted);
    } catch (Exception ex) {
      throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Sessao invalida", ex);
    }
  }

  private SecretKeySpec keySpec() {
    return new SecretKeySpec(cryptoSecret.getBytes(StandardCharsets.UTF_8), "AES");
  }
}
