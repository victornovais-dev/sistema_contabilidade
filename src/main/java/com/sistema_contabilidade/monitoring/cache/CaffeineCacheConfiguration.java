package com.sistema_contabilidade.monitoring.cache;

import com.github.benmanes.caffeine.cache.Cache;
import com.sistema_contabilidade.item.config.ItemDescricaoCatalog;
import com.sistema_contabilidade.item.config.ItemTipoDocumentoCatalog;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
@EnableCaching
@EnableConfigurationProperties(CaffeineCacheProperties.class)
public class CaffeineCacheConfiguration {

  public static final String STICKY_WRITER_LOCAL_CACHE = "stickyWriterLocal";
  public static final String STICKY_WRITER_LOCAL_CACHE_BEAN = "stickyWriterLocalCache";

  @Bean
  CacheManager cacheManager(CaffeineCacheFactory cacheFactory, CaffeineCacheProperties properties) {
    CaffeineCacheManager cacheManager = new CaffeineCacheManager();
    register(
        cacheManager,
        CustomUserDetailsService.USER_DETAILS_CACHE,
        cacheFactory,
        properties.getUserDetails());
    register(
        cacheManager,
        ItemDescricaoCatalog.ITEM_DESCRICOES_CACHE,
        cacheFactory,
        properties.getItemDescricoes());
    register(
        cacheManager,
        ItemTipoDocumentoCatalog.ITEM_TIPOS_DOCUMENTO_CACHE,
        cacheFactory,
        properties.getItemTiposDocumento());
    return cacheManager;
  }

  @Bean(STICKY_WRITER_LOCAL_CACHE_BEAN)
  Cache<UUID, Boolean> stickyWriterLocalCache(
      CaffeineCacheFactory cacheFactory,
      CaffeineCacheProperties properties,
      @Value("${app.database.sticky-writer.seconds:10}") long ttlSeconds) {
    return cacheFactory.create(
        STICKY_WRITER_LOCAL_CACHE,
        properties.getStickyWriter().getMaximumSize(),
        Duration.ofSeconds(ttlSeconds));
  }

  private static void register(
      CaffeineCacheManager cacheManager,
      String cacheName,
      CaffeineCacheFactory cacheFactory,
      CaffeineCacheProperties.CachePolicy policy) {
    Cache<Object, Object> cache =
        cacheFactory.create(cacheName, policy.getMaximumSize(), policy.getExpireAfterWrite());
    cacheManager.registerCustomCache(cacheName, cache);
  }
}
