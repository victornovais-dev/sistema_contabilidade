package com.sistema_contabilidade.monitoring.cache;

import static org.assertj.core.api.Assertions.assertThat;

import com.github.benmanes.caffeine.cache.Cache;
import com.sistema_contabilidade.security.service.CustomUserDetailsService;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.http.HttpStatus;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Caffeine cache Prometheus integration tests")
class CaffeineCachePrometheusIntegrationTest {

  @LocalServerPort private int port;
  @Autowired private CacheManager cacheManager;

  @Autowired
  @Qualifier(CaffeineCacheConfiguration.STICKY_WRITER_LOCAL_CACHE_BEAN)
  private Cache<UUID, Boolean> stickyWriterLocalCache;

  private final HttpClient httpClient = HttpClient.newHttpClient();

  @Test
  @DisplayName("Actuator Prometheus deve expor capacidade e resultados dos caches locais")
  void actuatorPrometheusDeveExporCachesLocais() throws IOException, InterruptedException {
    Cache<Object, Object> userDetails = nativeCache(CustomUserDetailsService.USER_DETAILS_CACHE);
    userDetails.getIfPresent("missing");
    userDetails.put("known", "value");
    userDetails.getIfPresent("known");
    UUID sessionId = UUID.randomUUID();
    stickyWriterLocalCache.getIfPresent(sessionId);
    stickyWriterLocalCache.put(sessionId, Boolean.TRUE);
    stickyWriterLocalCache.getIfPresent(sessionId);

    HttpResponse<String> response = get("/actuator/prometheus");

    assertThat(response.statusCode()).isEqualTo(HttpStatus.OK.value());
    assertThat(response.body())
        .contains("app_cache_size")
        .contains("app_cache_maximum_entries")
        .contains("app_cache_expiration_seconds")
        .contains("app_cache_requests_total")
        .contains("app_cache_evictions_total")
        .contains("cache=\"userDetails\"")
        .contains("cache=\"itemDescricoes\"")
        .contains("cache=\"itemTiposDocumento\"")
        .contains("cache=\"stickyWriterLocal\"");
  }

  @SuppressWarnings("unchecked")
  private Cache<Object, Object> nativeCache(String cacheName) {
    org.springframework.cache.Cache springCache = cacheManager.getCache(cacheName);
    assertThat(springCache).isInstanceOf(CaffeineCache.class);
    return (Cache<Object, Object>) springCache.getNativeCache();
  }

  private HttpResponse<String> get(String path) throws IOException, InterruptedException {
    HttpRequest request =
        HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).GET().build();
    return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
  }
}
