package com.sistema_contabilidade.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import jakarta.annotation.PostConstruct;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Date;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${app.jwt.ec-private-key:}")
  private String ecPrivateKey;

  @Value("${app.jwt.ec-public-key:}")
  private String ecPublicKey;

  @Value("${app.jwt.expiration-minutes:60}")
  private long expirationMinutes;

  private PrivateKey signingKey;
  private PublicKey verificationKey;

  @PostConstruct
  void initializeKeys() {
    if (ecPrivateKey == null
        || ecPrivateKey.isBlank()
        || ecPublicKey == null
        || ecPublicKey.isBlank()) {
      generateEphemeralKeys();
      return;
    }

    try {
      byte[] privateKeyBytes = decodeKeyMaterial(ecPrivateKey);
      byte[] publicKeyBytes = decodeKeyMaterial(ecPublicKey);
      KeyFactory keyFactory = KeyFactory.getInstance("EC");
      signingKey = keyFactory.generatePrivate(new PKCS8EncodedKeySpec(privateKeyBytes));
      verificationKey = keyFactory.generatePublic(new X509EncodedKeySpec(publicKeyBytes));
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Nao foi possivel carregar as chaves EC do JWT", e);
    }
  }

  public String generateToken(UserDetails userDetails) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(signingKey, Jwts.SIG.ES256)
        .compact();
  }

  public String extractUsername(String token) {
    return extractClaims(token).getSubject();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    Instant expiration = extractClaims(token).getExpiration().toInstant();
    return username.equals(userDetails.getUsername()) && expiration.isAfter(Instant.now());
  }

  private Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(verificationKey).build().parseSignedClaims(token).getPayload();
  }

  private byte[] decodeKeyMaterial(String keyMaterial) {
    String sanitized =
        keyMaterial
            .replace("-----BEGIN PRIVATE KEY-----", "")
            .replace("-----END PRIVATE KEY-----", "")
            .replace("-----BEGIN PUBLIC KEY-----", "")
            .replace("-----END PUBLIC KEY-----", "")
            .replaceAll("\\s", "");
    return Base64.getDecoder().decode(sanitized);
  }

  private void generateEphemeralKeys() {
    try {
      KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("EC");
      keyPairGenerator.initialize(256);
      KeyPair keyPair = keyPairGenerator.generateKeyPair();
      signingKey = keyPair.getPrivate();
      verificationKey = keyPair.getPublic();
    } catch (GeneralSecurityException e) {
      throw new IllegalStateException("Nao foi possivel gerar chaves EC para JWT", e);
    }
  }
}
