package com.sistema_contabilidade.security.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public final class ValkeyRateLimitService {

  static final String KEY_PREFIX = "sc:rate-limit:v1:";
  static final String VALKEY_ERROR_METRIC = "app.valkey.operation.errors";
  static final String SCRIPT_TEXT =
      """
      local redisTime = redis.call('TIME')
      local nowMillis = (redisTime[1] * 1000) + math.floor(redisTime[2] / 1000)
      local windowMillis = tonumber(ARGV[1])
      local maxRequests = tonumber(ARGV[2])
      local cutoff = nowMillis - windowMillis
      redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', cutoff)
      local requestCount = redis.call('ZCARD', KEYS[1])
      if requestCount >= maxRequests then
        return 0
      end
      redis.call('ZADD', KEYS[1], nowMillis, ARGV[3])
      redis.call('PEXPIRE', KEYS[1], windowMillis)
      return 1
      """;

  private static final DefaultRedisScript<Long> SLIDING_WINDOW_SCRIPT =
      new DefaultRedisScript<>(SCRIPT_TEXT, Long.class);

  private final StringRedisTemplate redisTemplate;
  private final int maxRequests;
  private final long windowMillis;
  private final boolean enabled;
  private final Counter errorCounter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification = "StringRedisTemplate is an injected Spring bean and is intentionally shared.")
  public ValkeyRateLimitService(
      StringRedisTemplate redisTemplate,
      MeterRegistry meterRegistry,
      @Value("${app.security.rate-limit.max-requests:120}") int maxRequests,
      @Value("${app.security.rate-limit.window-seconds:60}") long windowSeconds,
      @Value("${app.security.rate-limit.valkey-enabled:false}") boolean enabled) {
    if (maxRequests <= 0) {
      throw new IllegalArgumentException("Limite de requisicoes deve ser maior que zero");
    }
    if (windowSeconds <= 0) {
      throw new IllegalArgumentException("Janela do rate limit deve ser maior que zero");
    }
    this.redisTemplate = redisTemplate;
    this.maxRequests = maxRequests;
    this.windowMillis = Math.multiplyExact(windowSeconds, 1000L);
    this.enabled = enabled;
    this.errorCounter =
        Counter.builder(VALKEY_ERROR_METRIC)
            .description("Falhas de operacoes da aplicacao no Valkey")
            .tag("operation", "rate_limit")
            .register(meterRegistry);
  }

  public boolean isEnabled() {
    return enabled;
  }

  public RateLimitDecision tryAcquire(String clientKey) {
    Objects.requireNonNull(clientKey, "clientKey");
    if (!enabled) {
      return RateLimitDecision.UNAVAILABLE;
    }
    try {
      Long result =
          redisTemplate.execute(
              SLIDING_WINDOW_SCRIPT,
              List.of(KEY_PREFIX + clientKey),
              Long.toString(windowMillis),
              Integer.toString(maxRequests),
              UUID.randomUUID().toString());
      if (Objects.equals(result, 1L)) {
        return RateLimitDecision.ALLOWED;
      }
      if (Objects.equals(result, 0L)) {
        return RateLimitDecision.REJECTED;
      }
      return unavailable("Resposta invalida do script de rate limit no Valkey", null);
    } catch (RuntimeException exception) {
      return unavailable(
          "Falha no rate limit global do Valkey; fallback local sera usado", exception);
    }
  }

  private RateLimitDecision unavailable(String message, RuntimeException exception) {
    errorCounter.increment();
    if (exception == null) {
      log.debug(message);
    } else {
      log.debug(message, exception);
    }
    return RateLimitDecision.UNAVAILABLE;
  }
}
