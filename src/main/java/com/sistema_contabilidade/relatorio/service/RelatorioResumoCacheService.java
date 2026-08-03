package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.monitoring.cache.ValkeyVersionedJsonCache;
import com.sistema_contabilidade.monitoring.cache.ValkeyVersionedJsonCacheConfiguration;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import com.sistema_contabilidade.relatorio.dto.RelatorioFinanceiroResumoResponse;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public final class RelatorioResumoCacheService {

  static final String CACHE_KEY_PREFIX = "relatorio:resumo:v1:";
  static final String VERSION_KEY = "relatorio:resumo:version";
  static final String CACHE_METRIC = "app.relatorio.resumo.cache.total";
  static final String VALKEY_ERROR_METRIC = "app.valkey.operation.errors";

  private final ValkeyVersionedJsonCache<RelatorioFinanceiroResumoResponse> cache;

  public RelatorioResumoCacheService(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${app.relatorio.resumo-cache.ttl-seconds:30}") long ttlSeconds,
      @Value("${app.relatorio.resumo-cache.jitter-seconds:3}") long jitterSeconds,
      @Value("${app.relatorio.resumo-cache.max-bytes:131072}") int maxBytes,
      @Value("${app.relatorio.resumo-cache.enabled:false}") boolean enabled) {
    this.cache =
        new ValkeyVersionedJsonCache<>(
            redisTemplate,
            objectMapper,
            meterRegistry,
            new ValkeyVersionedJsonCacheConfiguration<>(
                RelatorioFinanceiroResumoResponse.class,
                CACHE_KEY_PREFIX,
                VERSION_KEY,
                CACHE_METRIC,
                "Resultados do cache Valkey do resumo financeiro",
                "relatorio_resumo_cache",
                "cache de resumo financeiro",
                ttlSeconds,
                jitterSeconds,
                maxBytes,
                enabled));
  }

  public RelatorioFinanceiroResumoResponse getOrCompute(
      CampaignScope scope,
      String normalizedFilters,
      Supplier<RelatorioFinanceiroResumoResponse> loader) {
    return cache.getOrCompute(scope, normalizedFilters, loader);
  }

  public void invalidateAfterItemWrite() {
    cache.invalidateAfterItemWrite();
  }
}
