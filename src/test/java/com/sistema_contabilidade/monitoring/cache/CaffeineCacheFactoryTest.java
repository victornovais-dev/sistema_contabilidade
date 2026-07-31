package com.sistema_contabilidade.monitoring.cache;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.github.benmanes.caffeine.cache.Cache;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("CaffeineCacheFactory unit tests")
class CaffeineCacheFactoryTest {

  private SimpleMeterRegistry meterRegistry;
  private CaffeineCacheFactory cacheFactory;

  @BeforeEach
  void setUp() {
    meterRegistry = new SimpleMeterRegistry();
    cacheFactory = new CaffeineCacheFactory(meterRegistry);
  }

  @Test
  @DisplayName("Deve limitar entradas e registrar hits, misses e evicoes")
  void deveLimitarEntradasERegistrarMetricas() {
    Cache<String, String> cache = cacheFactory.create("boundedCache", 2L, Duration.ofMinutes(1));

    cache.getIfPresent("missing");
    cache.put("first", "one");
    cache.getIfPresent("first");
    cache.put("second", "two");
    cache.put("third", "three");
    cache.cleanUp();

    assertThat(cache.estimatedSize()).isLessThanOrEqualTo(2L);
    assertThat(cache.policy().eviction().orElseThrow().getMaximum()).isEqualTo(2L);
    assertThat(
            meterRegistry
                .get(CaffeineCacheFactory.REQUESTS_METRIC)
                .tags("cache", "boundedCache", "result", "hit")
                .functionCounter()
                .count())
        .isEqualTo(1.0d);
    assertThat(
            meterRegistry
                .get(CaffeineCacheFactory.REQUESTS_METRIC)
                .tags("cache", "boundedCache", "result", "miss")
                .functionCounter()
                .count())
        .isEqualTo(1.0d);
    assertThat(
            meterRegistry
                .get(CaffeineCacheFactory.EVICTIONS_METRIC)
                .tag("cache", "boundedCache")
                .functionCounter()
                .count())
        .isGreaterThanOrEqualTo(1.0d);
  }

  @Test
  @DisplayName("Deve rejeitar limite e TTL invalidos")
  void deveRejeitarConfiguracaoInvalida() {
    Duration validTtl = Duration.ofMinutes(1);

    assertThatThrownBy(() -> cacheFactory.create("invalidSize", 0L, validTtl))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Limite");
    assertThatThrownBy(() -> cacheFactory.create("invalidTtl", 1L, Duration.ZERO))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("TTL");
  }
}
