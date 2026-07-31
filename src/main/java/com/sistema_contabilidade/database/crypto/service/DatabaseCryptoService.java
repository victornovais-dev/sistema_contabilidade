package com.sistema_contabilidade.database.crypto.service;

import com.sistema_contabilidade.database.crypto.DatabaseCryptoException;
import com.sistema_contabilidade.database.crypto.DatabaseCryptoRegistry;
import jakarta.annotation.PostConstruct;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatabaseCryptoService {

  private static final String CIPHER_ALGORITHM = "AES/GCM/NoPadding";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final String ENCRYPTED_PREFIX = "enc:v1:";
  private static final byte PAYLOAD_VERSION = 1;
  private static final int GCM_TAG_BITS = 128;
  private static final int IV_BYTES = 12;
  private static final int MINIMUM_SECRET_BYTES = 32;
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final byte[] masterSecret;
  private SecretKey encryptionKey;
  private SecretKey blindIndexKey;

  public DatabaseCryptoService(@Value("${app.database.crypto-secret}") String cryptoSecret) {
    this.masterSecret = cryptoSecret.getBytes(StandardCharsets.UTF_8);
  }

  @PostConstruct
  void initializeKeys() {
    if (masterSecret.length < MINIMUM_SECRET_BYTES) {
      throw new IllegalStateException("app.database.crypto-secret precisa ter ao menos 32 bytes");
    }
    this.encryptionKey = new SecretKeySpec(deriveKey("column-encryption-v1"), "AES");
    this.blindIndexKey = new SecretKeySpec(deriveKey("blind-index-v1"), HMAC_ALGORITHM);
    java.util.Arrays.fill(masterSecret, (byte) 0);
    DatabaseCryptoRegistry.register(this);
  }

  public String encrypt(String plaintext) {
    if (plaintext == null || plaintext.startsWith(ENCRYPTED_PREFIX)) {
      return plaintext;
    }
    try {
      byte[] iv = new byte[IV_BYTES];
      SECURE_RANDOM.nextBytes(iv);
      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
      byte[] payload =
          ByteBuffer.allocate(1 + iv.length + ciphertext.length)
              .put(PAYLOAD_VERSION)
              .put(iv)
              .put(ciphertext)
              .array();
      return ENCRYPTED_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(payload);
    } catch (GeneralSecurityException exception) {
      throw new DatabaseCryptoException("Falha ao criptografar coluna do banco", exception);
    }
  }

  public String decrypt(String storedValue) {
    if (storedValue == null || !storedValue.startsWith(ENCRYPTED_PREFIX)) {
      return storedValue;
    }
    try {
      byte[] payload =
          Base64.getUrlDecoder().decode(storedValue.substring(ENCRYPTED_PREFIX.length()));
      if (payload.length <= 1 + IV_BYTES || payload[0] != PAYLOAD_VERSION) {
        throw new DatabaseCryptoException("Payload criptografado invalido");
      }
      byte[] iv = java.util.Arrays.copyOfRange(payload, 1, 1 + IV_BYTES);
      byte[] ciphertext = java.util.Arrays.copyOfRange(payload, 1 + IV_BYTES, payload.length);
      Cipher cipher = Cipher.getInstance(CIPHER_ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, encryptionKey, new GCMParameterSpec(GCM_TAG_BITS, iv));
      return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    } catch (GeneralSecurityException | IllegalArgumentException exception) {
      throw new DatabaseCryptoException("Falha ao descriptografar coluna do banco", exception);
    }
  }

  public String blindIndex(String context, String normalizedValue) {
    if (normalizedValue == null || normalizedValue.isBlank()) {
      return null;
    }
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(blindIndexKey);
      mac.update(context.getBytes(StandardCharsets.UTF_8));
      mac.update((byte) 0);
      byte[] digest = mac.doFinal(normalizedValue.getBytes(StandardCharsets.UTF_8));
      return java.util.HexFormat.of().formatHex(digest);
    } catch (GeneralSecurityException exception) {
      throw new DatabaseCryptoException("Falha ao calcular blind index", exception);
    }
  }

  public boolean isEncrypted(String value) {
    return value == null || value.startsWith(ENCRYPTED_PREFIX);
  }

  private byte[] deriveKey(String context) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(masterSecret, HMAC_ALGORITHM));
      return mac.doFinal(context.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException exception) {
      throw new DatabaseCryptoException("Falha ao derivar chave criptografica", exception);
    }
  }
}
