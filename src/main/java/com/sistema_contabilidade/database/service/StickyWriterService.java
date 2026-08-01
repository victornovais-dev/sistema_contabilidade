package com.sistema_contabilidade.database.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.sistema_contabilidade.monitoring.cache.CaffeineCacheConfiguration;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import java.util.function.LongSupplier;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public final class StickyWriterService {

  static final String KEY_PREFIX = "sc:db-sticky:v1:";
  static final String STICKY_METRIC = "app.db.sticky.total";
  private static final String MARKER_VERSION = "v1";
  private static final String MARKER_HMAC_ALGORITHM = "HmacSHA256";
  private static final String COOKIE_SAME_SITE = "Strict";
  private static final String SESSION_ID_PARAMETER_NAME = "sessionId";
  private static final int MIN_MARKER_SECRET_LENGTH = 32;

  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;
  private final boolean routingEnabled;
  private final boolean valkeyEnabled;
  private final Cache<UUID, Boolean> localStickySessions;
  private final String signedMarkerCookieName;
  private final byte[] markerSecret;
  private final LongSupplier epochSeconds;
  private final Counter activeCounter;
  private final Counter inactiveCounter;
  private final Counter markerActiveCounter;
  private final Counter markerInvalidCounter;
  private final Counter markerExpiredCounter;
  private final Counter readErrorCounter;
  private final Counter markedCounter;
  private final Counter writeErrorCounter;
  private final Counter disabledCounter;

  @Autowired
  public StickyWriterService(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      @Qualifier(CaffeineCacheConfiguration.STICKY_WRITER_LOCAL_CACHE_BEAN)
          Cache<UUID, Boolean> localStickySessions,
      @Value("${app.database.sticky-writer.seconds:10}") long ttlSeconds,
      @Value("${app.database.routing.enabled:false}") boolean routingEnabled,
      @Value("${app.database.sticky-writer.valkey-enabled:false}") boolean valkeyEnabled,
      @Value("${app.database.sticky-writer.marker-cookie-name:SC_DB_STICKY}")
          String markerCookieName,
      @Value("${app.database.sticky-writer.marker-secret:${SESSION_CRYPTO_SECRET:}}")
          String markerSecret) {
    this(
        redisTemplate,
        meterRegistry,
        localStickySessions,
        ttlSeconds,
        routingEnabled,
        valkeyEnabled,
        markerCookieName,
        markerSecret,
        () -> Instant.now().getEpochSecond());
  }

  StickyWriterService(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      Cache<UUID, Boolean> localStickySessions,
      long ttlSeconds,
      boolean routingEnabled,
      boolean valkeyEnabled,
      String markerCookieName,
      String markerSecret,
      LongSupplier epochSeconds) {
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("Sticky writer TTL deve ser maior que zero");
    }
    if (markerCookieName == null || markerCookieName.isBlank()) {
      throw new IllegalArgumentException("Cookie do marcador sticky writer deve ser informado");
    }
    byte[] secretBytes = markerSecret.getBytes(StandardCharsets.UTF_8);
    if (secretBytes.length < MIN_MARKER_SECRET_LENGTH) {
      throw new IllegalArgumentException(
          "Segredo do marcador sticky writer precisa ter ao menos 32 bytes");
    }
    this.redisTemplate = redisTemplate;
    this.ttl = Duration.ofSeconds(ttlSeconds);
    this.routingEnabled = routingEnabled;
    this.valkeyEnabled = valkeyEnabled;
    this.localStickySessions = localStickySessions;
    this.signedMarkerCookieName = markerCookieName;
    this.markerSecret = secretBytes;
    this.epochSeconds = epochSeconds;
    this.activeCounter = counter(meterRegistry, "active");
    this.inactiveCounter = counter(meterRegistry, "inactive");
    this.markerActiveCounter = counter(meterRegistry, "marker_active");
    this.markerInvalidCounter = counter(meterRegistry, "marker_invalid");
    this.markerExpiredCounter = counter(meterRegistry, "marker_expired");
    this.readErrorCounter = counter(meterRegistry, "read_error");
    this.markedCounter = counter(meterRegistry, "marked");
    this.writeErrorCounter = counter(meterRegistry, "write_error");
    this.disabledCounter = counter(meterRegistry, "disabled");
  }

  public boolean requiresWriter(UUID sessionId) {
    return requiresWriter(sessionId, null);
  }

  public boolean requiresWriter(UUID sessionId, String signedMarker) {
    Objects.requireNonNull(sessionId, SESSION_ID_PARAMETER_NAME);
    if (!routingEnabled) {
      disabledCounter.increment();
      return false;
    }
    if (!valkeyEnabled) {
      return localRequiresWriter(sessionId);
    }
    try {
      boolean active = Boolean.TRUE.equals(redisTemplate.hasKey(key(sessionId)));
      if (active) {
        activeCounter.increment();
        return true;
      }
      MarkerStatus markerStatus = markerStatus(sessionId, signedMarker);
      if (markerStatus == MarkerStatus.ACTIVE) {
        markerActiveCounter.increment();
        return true;
      }
      if (markerStatus == MarkerStatus.INVALID) {
        markerInvalidCounter.increment();
        return true;
      }
      if (markerStatus == MarkerStatus.EXPIRED) {
        markerExpiredCounter.increment();
      } else {
        inactiveCounter.increment();
      }
      return false;
    } catch (RuntimeException exception) {
      readErrorCounter.increment();
      log.debug("Falha ao consultar sticky writer no Valkey; writer sera usado", exception);
      return true;
    }
  }

  public void markWriter(UUID sessionId) {
    Objects.requireNonNull(sessionId, SESSION_ID_PARAMETER_NAME);
    if (!routingEnabled) {
      disabledCounter.increment();
      return;
    }
    if (!valkeyEnabled) {
      localStickySessions.put(sessionId, Boolean.TRUE);
      markedCounter.increment();
      return;
    }
    try {
      redisTemplate.opsForValue().set(key(sessionId), "1", ttl);
      markedCounter.increment();
    } catch (RuntimeException exception) {
      writeErrorCounter.increment();
      log.debug("Falha ao renovar sticky writer no Valkey; mutacao sera preservada", exception);
    }
  }

  public Optional<ResponseCookie> signedMarkerCookie(UUID sessionId, boolean secure) {
    Objects.requireNonNull(sessionId, SESSION_ID_PARAMETER_NAME);
    if (!routingEnabled) {
      return Optional.empty();
    }
    return Optional.of(
        ResponseCookie.from(signedMarkerCookieName, createSignedMarker(sessionId))
            .httpOnly(true)
            .secure(secure)
            .sameSite(COOKIE_SAME_SITE)
            .path("/")
            .maxAge(ttl)
            .build());
  }

  public ResponseCookie clearSignedMarkerCookie(boolean secure) {
    return ResponseCookie.from(signedMarkerCookieName, "")
        .httpOnly(true)
        .secure(secure)
        .sameSite(COOKIE_SAME_SITE)
        .path("/")
        .maxAge(Duration.ZERO)
        .build();
  }

  public String markerCookieName() {
    return signedMarkerCookieName;
  }

  String createSignedMarker(UUID sessionId) {
    long expirationEpochSecond = Math.addExact(epochSeconds.getAsLong(), ttl.toSeconds());
    String expiration = Long.toString(expirationEpochSecond);
    String signature = sign(sessionId, expiration);
    return MARKER_VERSION + "." + expiration + "." + signature;
  }

  private String key(UUID sessionId) {
    return KEY_PREFIX + sessionId;
  }

  private boolean localRequiresWriter(UUID sessionId) {
    boolean active = localStickySessions.getIfPresent(sessionId) != null;
    if (active) {
      activeCounter.increment();
    } else {
      inactiveCounter.increment();
    }
    return active;
  }

  private MarkerStatus markerStatus(UUID sessionId, String signedMarker) {
    if (signedMarker == null || signedMarker.isBlank()) {
      return MarkerStatus.ABSENT;
    }
    String[] parts = signedMarker.split("\\.", -1);
    if (parts.length != 3 || !MARKER_VERSION.equals(parts[0])) {
      return MarkerStatus.INVALID;
    }
    long expirationEpochSecond;
    try {
      expirationEpochSecond = Long.parseLong(parts[1]);
    } catch (NumberFormatException _) {
      return MarkerStatus.INVALID;
    }
    if (expirationEpochSecond <= epochSeconds.getAsLong()) {
      return MarkerStatus.EXPIRED;
    }
    try {
      byte[] expectedSignature = Base64.getUrlDecoder().decode(sign(sessionId, parts[1]));
      byte[] suppliedSignature = Base64.getUrlDecoder().decode(parts[2]);
      return MessageDigest.isEqual(expectedSignature, suppliedSignature)
          ? MarkerStatus.ACTIVE
          : MarkerStatus.INVALID;
    } catch (IllegalArgumentException _) {
      return MarkerStatus.INVALID;
    }
  }

  private String sign(UUID sessionId, String expiration) {
    try {
      Mac mac = Mac.getInstance(MARKER_HMAC_ALGORITHM);
      mac.init(new SecretKeySpec(markerSecret, MARKER_HMAC_ALGORITHM));
      String payload = MARKER_VERSION + ":" + sessionId + ":" + expiration;
      return Base64.getUrlEncoder()
          .withoutPadding()
          .encodeToString(mac.doFinal(payload.getBytes(StandardCharsets.UTF_8)));
    } catch (java.security.GeneralSecurityException exception) {
      throw new IllegalStateException("HMAC do marcador sticky writer indisponivel", exception);
    }
  }

  private Counter counter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(STICKY_METRIC)
        .description("Consultas e renovacoes do sticky writer por sessao")
        .tag("result", result)
        .register(meterRegistry);
  }

  private enum MarkerStatus {
    ABSENT,
    ACTIVE,
    EXPIRED,
    INVALID
  }
}
