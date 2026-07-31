package com.sistema_contabilidade.monitoring.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.sistema_contabilidade.item.config.ItemDescricaoCatalog;
import com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;

@SpringBootTest(
    properties = {
      "app.cache.caffeine.user-details.maximum-size=3",
      "app.cache.caffeine.user-details.expire-after-write=2m",
      "app.cache.caffeine.item-descricoes.maximum-size=4",
      "app.cache.caffeine.item-descricoes.expire-after-write=3m",
      "app.cache.caffeine.item-tipos-documento.maximum-size=5",
      "app.cache.caffeine.item-tipos-documento.expire-after-write=4m",
      "app.cache.caffeine.sticky-writer.maximum-size=6",
      "app.database.sticky-writer.seconds=7"
    })
@DisplayName("Caffeine cache configuration integration tests")
class CaffeineCacheConfigurationIntegrationTest {

  @Autowired private CacheManager cacheManager;

  @Autowired
  @Qualifier(CaffeineCacheConfiguration.STICKY_WRITER_LOCAL_CACHE_BEAN)
  private Cache<UUID, Boolean> stickyWriterLocalCache;

  @Test
  @DisplayName("Deve aplicar limite e TTL especificos em todos os caches locais")
  void deveAplicarPoliticasEspecificas() {
    assertPolicy(
        nativeCache(CustomUserDetailsService.USER_DETAILS_CACHE), 3L, Duration.ofMinutes(2));
    assertPolicy(
        nativeCache(ItemDescricaoCatalog.ITEM_DESCRICOES_CACHE), 4L, Duration.ofMinutes(3));
    assertPolicy(
        nativeCache(ItemTipoDocumentoCatalog.ITEM_TIPOS_DOCUMENTO_CACHE),
        5L,
        Duration.ofMinutes(4));
    assertPolicy(stickyWriterLocalCache, 6L, Duration.ofSeconds(7));
  }

  @SuppressWarnings("unchecked")
  private Cache<Object, Object> nativeCache(String cacheName) {
    org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
    assertThat(springCache).isInstanceOf(CaffeineCache.class);
    return (Cache<Object, Object>) springCache.getNativeCache();
  }

  private static void assertPolicy(Cache<?, ?> cache, long maximumSize, Duration expireAfterWrite) {
    assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(maximumSize);
    assertThat(cache.policy().expireAfterWrite().orElseThrow().getExpiresAfter())
        .isEqualTo(expireAfterWrite);
  }
}
