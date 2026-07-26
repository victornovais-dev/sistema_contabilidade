package com.sistema_contabilidade.security.service;

import static org.assertj.core.api.Assertions.assertThat;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

@Testcontainers(disabledWithoutDocker = true)
@DisplayName("ValkeyRateLimitService integration tests")
class ValkeyRateLimitServiceIntegrationTest {

  @Container
  private static final GenericContainer<?> VALKEY =
      new GenericContainer<>(DockerImageName.parse("valkey/valkey:8.0-alpine"))
          .withExposedPorts(6379);

  private static LettuceConnectionFactory connectionFactory;
  private static StringRedisTemplate redisTemplate;

  @BeforeAll
  static void configureRedis() {
    connectionFactory = new LettuceConnectionFactory(VALKEY.getHost(), VALKEY.getMappedPort(6379));
    connectionFactory.afterPropertiesSet();
    redisTemplate = new StringRedisTemplate(connectionFactory);
    redisTemplate.afterPropertiesSet();
  }

  @AfterAll
  static void closeRedis() {
    connectionFactory.destroy();
  }

  @BeforeEach
  void clearRedis() {
    try (RedisConnection connection = connectionFactory.getConnection()) {
      connection.serverCommands().flushDb();
    }
  }

  @Test
  @DisplayName("Duas instancias devem compartilhar limite global")
  void duasInstanciasDevemCompartilharLimiteGlobal() {
    ValkeyRateLimitService first = service(3);
    ValkeyRateLimitService second = service(3);

    assertThat(first.tryAcquire("shared")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(second.tryAcquire("shared")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(first.tryAcquire("shared")).isEqualTo(RateLimitDecision.ALLOWED);
    assertThat(second.tryAcquire("shared")).isEqualTo(RateLimitDecision.REJECTED);
  }

  @Test
  @DisplayName("Lua atomico deve limitar requests concorrentes")
  void luaAtomicoDeveLimitarRequestsConcorrentes() throws Exception {
    int limit = 20;
    int requestCount = 100;
    ValkeyRateLimitService service = service(limit);
    CountDownLatch start = new CountDownLatch(1);
    ExecutorService executor = Executors.newFixedThreadPool(16);
    List<Future<RateLimitDecision>> futures = new ArrayList<>();
    try {
      for (int index = 0; index < requestCount; index++) {
        futures.add(
            executor.submit(
                () -> {
                  start.await();
                  return service.tryAcquire("concurrent");
                }));
      }
      start.countDown();
      long allowed = 0;
      for (Future<RateLimitDecision> future : futures) {
        if (future.get() == RateLimitDecision.ALLOWED) {
          allowed++;
        }
      }
      assertThat(allowed).isEqualTo(limit);
    } finally {
      executor.shutdownNow();
    }
  }

  private ValkeyRateLimitService service(int maxRequests) {
    return new ValkeyRateLimitService(
        redisTemplate, new SimpleMeterRegistry(), maxRequests, 60, true);
  }
}
