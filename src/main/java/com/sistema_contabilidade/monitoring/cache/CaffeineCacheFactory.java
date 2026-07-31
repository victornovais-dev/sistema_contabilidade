package com.sistema_contabilidade.monitoring.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.FunctionCounter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CaffeineCacheFactory {

  public static final String SIZE_METRIC = "app.cache.size";
  public static final String MAXIMUM_ENTRIES_METRIC = "app.cache.maximum.entries";
  public static final String EXPIRATION_SECONDS_METRIC = "app.cache.expiration.seconds";
  public static final String REQUESTS_METRIC = "app.cache.requests";
  public static final String EVICTIONS_METRIC = "app.cache.evictions";

  private static final String CACHE_TAG = "cache";
  private static final String RESULT_TAG = "result";
  private static final long ZERO_ENTRIES = 0L;

  private final MeterRegistry meterRegistry;

  public <K, V> Cache<K, V> create(String cacheName, long maximumSize, Duration expireAfterWrite) {
    validate(cacheName, maximumSize, expireAfterWrite);
    Cache<K, V> cache =
        Caffeine.newBuilder()
            .maximumSize(maximumSize)
            .expireAfterWrite(expireAfterWrite)
            .recordStats()
            .build();
    registerMetrics(cacheName, maximumSize, expireAfterWrite, cache);
    return cache;
  }

  private <K, V> void registerMetrics(
      String cacheName, long maximumSize, Duration expireAfterWrite, Cache<K, V> cache) {
    Gauge.builder(SIZE_METRIC, cache, Cache::estimatedSize)
        .tag(CACHE_TAG, cacheName)
        .description("Current estimated number of entries in the local Caffeine cache")
        .register(meterRegistry);
    Gauge.builder(MAXIMUM_ENTRIES_METRIC, () -> maximumSize)
        .tag(CACHE_TAG, cacheName)
        .description("Configured maximum number of entries in the local Caffeine cache")
        .register(meterRegistry);
    Gauge.builder(EXPIRATION_SECONDS_METRIC, expireAfterWrite::toSeconds)
        .tag(CACHE_TAG, cacheName)
        .description("Configured expire-after-write duration for the local Caffeine cache")
        .register(meterRegistry);
    FunctionCounter.builder(REQUESTS_METRIC, cache, value -> value.stats().hitCount())
        .tag(CACHE_TAG, cacheName)
        .tag(RESULT_TAG, "hit")
        .description("Cumulative local Caffeine cache requests")
        .register(meterRegistry);
    FunctionCounter.builder(REQUESTS_METRIC, cache, value -> value.stats().missCount())
        .tag(CACHE_TAG, cacheName)
        .tag(RESULT_TAG, "miss")
        .description("Cumulative local Caffeine cache requests")
        .register(meterRegistry);
    FunctionCounter.builder(EVICTIONS_METRIC, cache, value -> value.stats().evictionCount())
        .tag(CACHE_TAG, cacheName)
        .description("Cumulative local Caffeine cache evictions")
        .register(meterRegistry);
  }

  private static void validate(String cacheName, long maximumSize, Duration expireAfterWrite) {
    if (Objects.requireNonNull(cacheName, "cacheName").isBlank()) {
      throw new IllegalArgumentException("Nome do cache Caffeine nao pode ser vazio");
    }
    if (maximumSize <= ZERO_ENTRIES) {
      throw new IllegalArgumentException("Limite do cache Caffeine deve ser maior que zero");
    }
    Objects.requireNonNull(expireAfterWrite, "expireAfterWrite");
    if (expireAfterWrite.isZero() || expireAfterWrite.isNegative()) {
      throw new IllegalArgumentException("TTL do cache Caffeine deve ser maior que zero");
    }
  }
}
