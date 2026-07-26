package com.sistema_contabilidade.database.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public final class StickyWriterService {

  static final String KEY_PREFIX = "sc:db-sticky:v1:";
  static final String STICKY_METRIC = "app.db.sticky.total";

  private final StringRedisTemplate redisTemplate;
  private final Duration ttl;
  private final boolean routingEnabled;
  private final Counter activeCounter;
  private final Counter inactiveCounter;
  private final Counter readErrorCounter;
  private final Counter markedCounter;
  private final Counter writeErrorCounter;
  private final Counter disabledCounter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "StringRedisTemplate is an injected Spring bean and is intentionally shared.")
  public StickyWriterService(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.database.sticky-writer.seconds:10}") long ttlSeconds,
      @Value("${app.database.routing.enabled:false}") boolean routingEnabled) {
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("Sticky writer TTL deve ser maior que zero");
    }
    this.redisTemplate = redisTemplate;
    this.ttl = Duration.ofSeconds(ttlSeconds);
    this.routingEnabled = routingEnabled;
    this.activeCounter = counter(meterRegistry, "active");
    this.inactiveCounter = counter(meterRegistry, "inactive");
    this.readErrorCounter = counter(meterRegistry, "read_error");
    this.markedCounter = counter(meterRegistry, "marked");
    this.writeErrorCounter = counter(meterRegistry, "write_error");
    this.disabledCounter = counter(meterRegistry, "disabled");
  }

  public boolean requiresWriter(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId");
    if (!routingEnabled) {
      disabledCounter.increment();
      return false;
    }
    try {
      boolean active = Boolean.TRUE.equals(redisTemplate.hasKey(key(sessionId)));
      if (active) {
        activeCounter.increment();
      } else {
        inactiveCounter.increment();
      }
      return active;
    } catch (RuntimeException exception) {
      readErrorCounter.increment();
      log.debug("Falha ao consultar sticky writer no Valkey; writer sera usado", exception);
      return true;
    }
  }

  public void markWriter(UUID sessionId) {
    Objects.requireNonNull(sessionId, "sessionId");
    if (!routingEnabled) {
      disabledCounter.increment();
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

  private String key(UUID sessionId) {
    return KEY_PREFIX + sessionId;
  }

  private Counter counter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(STICKY_METRIC)
        .description("Consultas e renovacoes do sticky writer por sessao")
        .tag("result", result)
        .register(meterRegistry);
  }
}
