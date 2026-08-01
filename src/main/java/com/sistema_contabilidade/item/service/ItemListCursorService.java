package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListCursorDirection;
import com.sistema_contabilidade.item.repository.ItemListKeysetCursor;
import com.sistema_contabilidade.item.repository.ItemListPageQuery;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public final class ItemListCursorService {

  private static final String VERSION = "v1";
  private static final String ENDPOINT = "GET:/api/v1/itens";
  private static final String HMAC_ALGORITHM = "HmacSHA256";
  private static final int MIN_SECRET_LENGTH = 32;
  private static final int PART_COUNT = 13;
  private static final Duration TTL = Duration.ofMinutes(10);
  private static final String INVALID_CURSOR_MESSAGE = "Cursor de paginacao invalido.";

  private final byte[] activeSecret;
  private final byte[] previousSecret;
  private final Clock clock;

  @Autowired
  public ItemListCursorService(
      @Value("${app.item-list.cursor.secret:${SESSION_CRYPTO_SECRET:}}") String activeSecret,
      @Value("${app.item-list.cursor.previous-secret:}") String previousSecret) {
    this(activeSecret, previousSecret, Clock.systemUTC());
  }

  ItemListCursorService(String activeSecret, String previousSecret, Clock clock) {
    this.activeSecret = requiredSecret(activeSecret, "Segredo ativo do cursor de itens");
    this.previousSecret = optionalSecret(previousSecret, "Segredo anterior do cursor de itens");
    this.clock = Objects.requireNonNull(clock, "clock");
  }

  public String create(ItemListPageQuery query, int pageSize, ItemListKeysetCursor position) {
    Objects.requireNonNull(query, "query");
    Objects.requireNonNull(position, "position");
    long expiresAt = Math.addExact(Instant.now(clock).plus(TTL).getEpochSecond(), 0);
    String unsigned =
        String.join(
            ".",
            VERSION,
            encode(ENDPOINT),
            Long.toString(expiresAt),
            encode(canonicalScope(query)),
            encode(enumName(query.tipo())),
            encode(stringValue(query.dataInicio())),
            encode(stringValue(query.dataFim())),
            encode(query.descricao()),
            encode(query.razao()),
            Integer.toString(pageSize),
            encode(position.horarioCriacao().toString()),
            position.id().toString());
    return unsigned + "." + signature(unsigned, activeSecret);
  }

  public ItemListKeysetCursor parse(
      String cursor, ItemListPageQuery query, int pageSize, ItemListCursorDirection direction) {
    if (cursor == null || cursor.isBlank()) {
      throw invalidCursor();
    }
    String[] parts = cursor.split("\\.", -1);
    if (parts.length != PART_COUNT || !VERSION.equals(parts[0])) {
      throw invalidCursor();
    }

    String unsigned = String.join(".", List.of(parts).subList(0, PART_COUNT - 1));
    if (!hasValidSignature(unsigned, parts[PART_COUNT - 1])) {
      throw invalidCursor();
    }

    try {
      long expiresAt = Long.parseLong(parts[2]);
      if (expiresAt <= Instant.now(clock).getEpochSecond()
          || !ENDPOINT.equals(decode(parts[1]))
          || !canonicalScope(query).equals(decode(parts[3]))
          || !enumName(query.tipo()).equals(decode(parts[4]))
          || !stringValue(query.dataInicio()).equals(decode(parts[5]))
          || !stringValue(query.dataFim()).equals(decode(parts[6]))
          || !stringValue(query.descricao()).equals(decode(parts[7]))
          || !stringValue(query.razao()).equals(decode(parts[8]))
          || pageSize != Integer.parseInt(parts[9])) {
        throw invalidCursor();
      }
      return new ItemListKeysetCursor(
          LocalDateTime.parse(decode(parts[10])), UUID.fromString(parts[11]), direction);
    } catch (IllegalArgumentException exception) {
      throw invalidCursor(exception);
    }
  }

  private boolean hasValidSignature(String unsigned, String suppliedSignature) {
    try {
      byte[] supplied = Base64.getUrlDecoder().decode(suppliedSignature);
      return matches(unsigned, activeSecret, supplied)
          || (previousSecret.length > 0 && matches(unsigned, previousSecret, supplied));
    } catch (IllegalArgumentException _) {
      return false;
    }
  }

  private boolean matches(String unsigned, byte[] secret, byte[] suppliedSignature) {
    return MessageDigest.isEqual(
        Base64.getUrlDecoder().decode(signature(unsigned, secret)), suppliedSignature);
  }

  private String signature(String unsigned, byte[] secret) {
    try {
      Mac mac = Mac.getInstance(HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(secret, HMAC_ALGORITHM));
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(unsigned.getBytes(StandardCharsets.UTF_8)));
    } catch (GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC do cursor de itens indisponivel", exception);
    }
  }

  private String canonicalScope(ItemListPageQuery query) {
    return query.roleNomes().stream()
        .filter(Objects::nonNull)
        .map(role -> role.trim().toUpperCase(Locale.ROOT))
        .filter(role -> !role.isBlank())
        .sorted()
        .reduce((left, right) -> left + "," + right)
        .orElse("");
  }

  private String enumName(Enum<?> value) {
    return value == null ? "" : value.name();
  }

  private String stringValue(Object value) {
    return value == null ? "" : value.toString();
  }

  private String encode(String value) {
    return Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(stringValue(value).getBytes(StandardCharsets.UTF_8));
  }

  private String decode(String value) {
    return new String(Base64.getUrlDecoder().decode(value), StandardCharsets.UTF_8);
  }

  private byte[] requiredSecret(String secret, String propertyName) {
    byte[] bytes = stringValue(secret).getBytes(StandardCharsets.UTF_8);
    if (bytes.length < MIN_SECRET_LENGTH) {
      throw new IllegalArgumentException(propertyName + " precisa ter ao menos 32 bytes");
    }
    return bytes;
  }

  private byte[] optionalSecret(String secret, String propertyName) {
    if (secret == null || secret.isBlank()) {
      return new byte[0];
    }
    return requiredSecret(secret, propertyName);
  }

  private ResponseStatusException invalidCursor() {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CURSOR_MESSAGE);
  }

  private ResponseStatusException invalidCursor(Throwable cause) {
    return new ResponseStatusException(HttpStatus.BAD_REQUEST, INVALID_CURSOR_MESSAGE, cause);
  }
}
