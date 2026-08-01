package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.database.routing.DatabaseRoutingContext;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HexFormat;
import java.util.Objects;
import java.util.function.Supplier;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
@Slf4j
public final class RelatorioResumoCacheService {

  static final String CACHE_KEY_PREFIX = "relatorio:resumo:v1:";
  static final String VERSION_KEY = "relatorio:resumo:version";
  static final String CACHE_METRIC = "app.relatorio.resumo.cache.total";
  static final String VALKEY_ERROR_METRIC = "app.valkey.operation.errors";

  private static final String ALL_ROLES = "ALL";

  private final StringRedisTemplate redisTemplate;
  private final ObjectMapper objectMapper;
  private final Duration ttl;
  private final int maxBytes;
  private final boolean enabled;
  private final Counter hitCounter;
  private final Counter missCounter;
  private final Counter bypassCounter;
  private final Counter errorCounter;
  private final Counter valkeyErrorCounter;

  @SuppressFBWarnings(
      value = "EI_EXPOSE_REP2",
      justification =
          "StringRedisTemplate e ObjectMapper sao beans Spring compartilhados e imutaveis para este servico.")
  public RelatorioResumoCacheService(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${app.relatorio.resumo-cache.ttl-seconds:30}") long ttlSeconds,
      @Value("${app.relatorio.resumo-cache.max-bytes:131072}") int maxBytes,
      @Value("${app.relatorio.resumo-cache.enabled:false}") boolean enabled) {
    if (ttlSeconds <= 0) {
      throw new IllegalArgumentException("TTL do cache de resumo deve ser maior que zero");
    }
    if (maxBytes <= 0) {
      throw new IllegalArgumentException("Limite do cache de resumo deve ser maior que zero");
    }
    this.redisTemplate = redisTemplate;
    this.objectMapper = objectMapper;
    this.ttl = Duration.ofSeconds(ttlSeconds);
    this.maxBytes = maxBytes;
    this.enabled = enabled;
    this.hitCounter = counter(meterRegistry, "hit");
    this.missCounter = counter(meterRegistry, "miss");
    this.bypassCounter = counter(meterRegistry, "bypass");
    this.errorCounter = counter(meterRegistry, "error");
    this.valkeyErrorCounter =
        Counter.builder(VALKEY_ERROR_METRIC)
            .description("Falhas de operacoes da aplicacao no Valkey")
            .tag("operation", "relatorio_resumo_cache")
            .register(meterRegistry);
  }

  public RelatorioFinanceiroResumoResponse getOrCompute(
      CampaignScope scope,
      String normalizedFilters,
      Supplier<RelatorioFinanceiroResumoResponse> loader) {
    Objects.requireNonNull(scope, "scope");
    Objects.requireNonNull(normalizedFilters, "normalizedFilters");
    Objects.requireNonNull(loader, "loader");
    if (!enabled || DatabaseRoutingContext.isStickyWriter()) {
      bypassCounter.increment();
      return loader.get();
    }

    String cacheKey;
    try {
      cacheKey = cacheKey(scope, normalizedFilters, currentVersion());
      String cachedPayload = redisTemplate.opsForValue().get(cacheKey);
      if (cachedPayload != null) {
        if (payloadSize(cachedPayload) > maxBytes) {
          bypassCounter.increment();
          return loader.get();
        }
        RelatorioFinanceiroResumoResponse cachedResponse =
            objectMapper.readValue(cachedPayload, RelatorioFinanceiroResumoResponse.class);
        hitCounter.increment();
        return cachedResponse;
      }
    } catch (Exception exception) {
      recordError("Falha ao ler resumo financeiro do Valkey; banco sera usado", exception);
      return loader.get();
    }

    RelatorioFinanceiroResumoResponse response = loader.get();
    try {
      String payload = objectMapper.writeValueAsString(response);
      if (payloadSize(payload) > maxBytes) {
        bypassCounter.increment();
        return response;
      }
      redisTemplate.opsForValue().set(cacheKey, payload, ttl);
      missCounter.increment();
    } catch (Exception exception) {
      recordError("Falha ao gravar resumo financeiro no Valkey", exception);
    }
    return response;
  }

  public void invalidateAfterItemWrite() {
    if (!enabled) {
      return;
    }
    try {
      Long version = redisTemplate.opsForValue().increment(VERSION_KEY);
      if (version == null) {
        throw new IllegalStateException("Valkey retornou versao nula para cache de resumo");
      }
    } catch (RuntimeException exception) {
      recordError("Falha ao invalidar cache de resumo financeiro no Valkey", exception);
    }
  }

  private String currentVersion() {
    String version = redisTemplate.opsForValue().get(VERSION_KEY);
    if (version == null) {
      return "0";
    }
    long parsedVersion = Long.parseLong(version);
    if (parsedVersion < 0) {
      throw new IllegalStateException("Versao invalida do cache de resumo");
    }
    return Long.toString(parsedVersion);
  }

  private String cacheKey(CampaignScope scope, String normalizedFilters, String version) {
    String role =
        scope.roleFilter() == null || scope.roleFilter().isBlank() ? ALL_ROLES : scope.roleFilter();
    return CACHE_KEY_PREFIX
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

  private Counter counter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(CACHE_METRIC)
        .description("Resultados do cache Valkey do resumo financeiro")
        .tag("result", result)
        .register(meterRegistry);
  }
}
