package com.sistema_contabilidade.monitoring.cache;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import tools.jackson.databind.ObjectMapper;

/** Versioned Valkey JSON cache for read models invalidated after item writes. */
@Slf4j
public final class ValkeyVersionedJsonCache<T> {

  private static final String ALL_ROLES = "ALL";
  private static final SecureRandom SECURE_RANDOM = new SecureRandom();

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final ValkeyVersionedJsonCacheConfiguration<T> configuration;
  private final Duration ttl;
  private final Counter hitCounter;
  private final Counter missCounter;
  private final Counter bypassCounter;
  private final Counter errorCounter;
  private final Counter valkeyErrorCounter;

  public ValkeyVersionedJsonCache(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      ValkeyVersionedJsonCacheConfiguration<T> configuration) {
    this.configuration = Objects.requireNonNull(configuration, "configuration");
    validate(configuration);
    this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate");
    this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
    this.ttl = Duration.ofSeconds(configuration.ttlSeconds());
    this.hitCounter = counter(meterRegistry, configuration, "hit");
    this.missCounter = counter(meterRegistry, configuration, "miss");
    this.bypassCounter = counter(meterRegistry, configuration, "bypass");
    this.errorCounter = counter(meterRegistry, configuration, "error");
    this.valkeyErrorCounter =
        Counter.builder("app.valkey.operation.errors")
            .description("Falhas de operacoes da aplicacao no Valkey")
            .tag("operation", configuration.valkeyOperation())
            .register(meterRegistry);
  }

  public T getOrCompute(CampaignScope scope, String normalizedFilters, Supplier<T> loader) {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(normalizedFilters, "normalizedFilters");
    Objects.requireNonNull(loader, "loader");
    if (!configuration.enabled() || DatabaseRoutingContext.isStickyWriter()) {
      bypassCounter.increment();
      return loader.get();
    }

    String cacheKey;
    try {
      cacheKey = cacheKey(scope, normalizedFilters, currentVersion());
      String cachedPayload = redisTemplate.opsForValue().get(cacheKey);
      if (cachedPayload != null) {
        if (payloadSize(cachedPayload) > configuration.maxBytes()) {
          bypassCounter.increment();
          return loader.get();
        }
        T cachedResponse = objectMapper.readValue(cachedPayload, configuration.responseType());
        hitCounter.increment();
        return cachedResponse;
      }
    } catch (Exception exception) {
      recordError("Falha ao ler " + configuration.cacheDescription() + " do Valkey", exception);
      return loader.get();
    }

    T response = loader.get();
    try {
      String payload = objectMapper.writeValueAsString(response);
      if (payloadSize(payload) > configuration.maxBytes()) {
        bypassCounter.increment();
        return response;
      }
      redisTemplate.opsForValue().set(cacheKey, payload, ttlWithJitter());
      missCounter.increment();
    } catch (Exception exception) {
      recordError("Falha ao gravar " + configuration.cacheDescription() + " no Valkey", exception);
    }
    return response;
  }

  public void invalidateAfterItemWrite() {
    if (!configuration.enabled()) {
      return;
    }
    try {
      Long version = redisTemplate.opsForValue().increment(configuration.versionKey());
      if (version == null) {
        throw new IllegalStateException(
            "Valkey retornou versao nula para " + configuration.cacheDescription());
      }
    } catch (RuntimeException exception) {
      recordError(
          "Falha ao invalidar " + configuration.cacheDescription() + " no Valkey", exception);
    }
  }

  private void validate(ValkeyVersionedJsonCacheConfiguration<T> configuration) {
    if (configuration.ttlSeconds() <= 0 || configuration.ttlSeconds() > 30) {
      throw new IllegalArgumentException("TTL do cache deve estar entre 1 e 30 segundos");
    }
    if (configuration.jitterSeconds() < 0
        || configuration.jitterSeconds() >= configuration.ttlSeconds()) {
      throw new IllegalArgumentException("Jitter do cache deve ser menor que o TTL");
    }
    if (configuration.maxBytes() <= 0) {
      throw new IllegalArgumentException("Limite do cache deve ser maior que zero");
    }
  }

  private String currentVersion() {
    String version = redisTemplate.opsForValue().get(configuration.versionKey());
    if (version == null) {
      return "0";
    }
    long parsedVersion = Long.parseLong(version);
    if (parsedVersion < 0) {
      throw new IllegalStateException("Versao invalida de " + configuration.cacheDescription());
    }
    return Long.toString(parsedVersion);
  }

  private String cacheKey(CampaignScope scope, String normalizedFilters, String version) {
    String role =
        scope.roleFilter() == null || scope.roleFilter().isBlank() ? ALL_ROLES : scope.roleFilter();
    return configuration.cacheKeyPrefix()
        + sha256(scope.canonicalCacheScope())
        + ':'
        + role
        + ':'
        + sha256(normalizedFilters)
        + ':'
        + version;
  }

  private int payloadSize(String payload) {
    return payload.getBytes(StandardCharsets.UTF_8).length;
  }

  private Duration ttlWithJitter() {
    if (configuration.jitterSeconds() == 0) {
      return ttl;
    }
    long minimumSeconds = ttl.getSeconds() - configuration.jitterSeconds();
    long ttlSeconds = SECURE_RANDOM.nextLong(minimumSeconds, ttl.getSeconds() + 1);
    return Duration.ofSeconds(ttlSeconds);
  }

  private String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException exception) {
      throw new IllegalStateException("SHA-256 indisponivel", exception);
    }
  }

  private void recordError(String message, Exception exception) {
    errorCounter.increment();
    valkeyErrorCounter.increment();
    log.debug(message, exception);
  }

  private Counter counter(
      MeterRegistry meterRegistry,
      ValkeyVersionedJsonCacheConfiguration<T> configuration,
      String result) {
    return Counter.builder(configuration.cacheMetric())
        .description(configuration.cacheMetricDescription())
        .tag("result", result)
        .register(meterRegistry);
  }
}
