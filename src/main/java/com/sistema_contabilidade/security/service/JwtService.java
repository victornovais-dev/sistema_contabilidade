package com.sistema_contabilidade.security.service;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Service;

@Service
public class JwtService {

  @Value("${app.jwt.secret}")
  private String jwtSecret;

  @Value("${app.jwt.expiration-minutes:60}")
  private long expirationMinutes;

  @PostConstruct
  void validateSecret() {
    if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
      throw new IllegalStateException("app.jwt.secret precisa ter ao menos 32 bytes");
    }
  }

  public String generateToken(UserDetails userDetails) {
    Instant now = Instant.now();
    return Jwts.builder()
        .subject(userDetails.getUsername())
        .issuedAt(Date.from(now))
        .expiration(Date.from(now.plus(expirationMinutes, ChronoUnit.MINUTES)))
        .signWith(secretKey())
        .compact();
  }

  public String extractUsername(String token) {
    return extractClaims(token).getSubject();
  }

  public boolean isTokenValid(String token, UserDetails userDetails) {
    String username = extractUsername(token);
    Date expiration = extractClaims(token).getExpiration();
    return username.equals(userDetails.getUsername()) && expiration.after(new Date());
  }

  private Claims extractClaims(String token) {
    return Jwts.parser().verifyWith(secretKey()).build().parseSignedClaims(token).getPayload();
  }

  private SecretKey secretKey() {
    return Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
  }
}
