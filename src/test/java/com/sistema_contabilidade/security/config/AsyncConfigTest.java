package com.sistema_contabilidade.security.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.concurrent.Executor;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

@DisplayName("AsyncConfig unit tests")
class AsyncConfigTest {

  @Test
  @DisplayName("Deve configurar executor de autenticacao")
  void deveConfigurarExecutorDeAutenticacao() {
    AsyncConfig config = new AsyncConfig();

    Executor executor = config.authExecutor();

    ThreadPoolTaskExecutor taskExecutor = assertInstanceOf(ThreadPoolTaskExecutor.class, executor);
    assertEquals(10, taskExecutor.getCorePoolSize());
    assertEquals(30, taskExecutor.getMaxPoolSize());
    assertEquals(50, taskExecutor.getQueueCapacity());
    assertEquals("auth-scrypt-", taskExecutor.getThreadNamePrefix());
    assertNotNull(taskExecutor.getThreadPoolExecutor());
  }
}
