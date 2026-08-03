package com.sistema_contabilidade.monitoring.cache;

/** Immutable configuration for a versioned Valkey JSON cache. */
public record ValkeyVersionedJsonCacheConfiguration<T>(
    Class<T> responseType,
    String cacheKeyPrefix,
    String versionKey,
    String cacheMetric,
    String cacheMetricDescription,
    String valkeyOperation,
    String cacheDescription,
    long ttlSeconds,
    long jitterSeconds,
    int maxBytes,
    boolean enabled) {}
