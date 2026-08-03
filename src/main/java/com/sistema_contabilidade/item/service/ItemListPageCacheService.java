package com.sistema_contabilidade.item.service;

import com.sistema_contabilidade.item.dto.ItemListPageResponse;
import com.sistema_contabilidade.monitoring.cache.ValkeyVersionedJsonCache;
import com.sistema_contabilidade.monitoring.cache.ValkeyVersionedJsonCacheConfiguration;
import com.sistema_contabilidade.rbac.service.CampaignScope;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.Supplier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.ObjectMapper;

@Service
public final class ItemListPageCacheService implements ItemListPageCache {

  static final String CACHE_KEY_PREFIX = "item:list:page:v1:";
  static final String VERSION_KEY = "item:list:page:version";
  static final String CACHE_METRIC = "app.item.list.cache.total";
  static final String VALKEY_ERROR_METRIC = "app.valkey.operation.errors";

  private final ValkeyVersionedJsonCache<ItemListPageResponse> cache;

  public ItemListPageCacheService(
      StringRedisTemplate redisTemplate,
      ObjectMapper objectMapper,
      MeterRegistry meterRegistry,
      @Value("${app.item-list.page-cache.ttl-seconds:30}") long ttlSeconds,
      @Value("${app.item-list.page-cache.jitter-seconds:3}") long jitterSeconds,
      @Value("${app.item-list.page-cache.max-bytes:131072}") int maxBytes,
      @Value("${app.item-list.page-cache.enabled:false}") boolean enabled) {
    this.cache =
        new ValkeyVersionedJsonCache<>(
            redisTemplate,
            objectMapper,
            meterRegistry,
            new ValkeyVersionedJsonCacheConfiguration<>(
                ItemListPageResponse.class,
                CACHE_KEY_PREFIX,
                VERSION_KEY,
                CACHE_METRIC,
                "Resultados do cache Valkey das paginas de itens",
                "item_list_page_cache",
                "cache de paginas da lista de itens",
                ttlSeconds,
                jitterSeconds,
                maxBytes,
                enabled));
  }

  @Override
  public ItemListPageResponse getOrCompute(
      CampaignScope scope, String normalizedFilters, Supplier<ItemListPageResponse> loader) {
    return cache.getOrCompute(scope, normalizedFilters, loader);
  }

  @Override
  public void invalidateAfterItemWrite() {
    cache.invalidateAfterItemWrite();
  }
}
