package com.sistema_contabilidade.relatorio.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeProbe;
import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeSnapshot;
import com.sistema_contabilidade.relatorio.config.PdfGenerationProperties;
import com.sistema_contabilidade.relatorio.exception.PdfGenerationCapacityExceededException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.time.Duration;
import java.util.OptionalLong;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("PdfGenerationLimiter unit tests")
class PdfGenerationLimiterTest {

  @Test
  @DisplayName("Deve limitar concorrencia, enfileirar ate o limite e rejeitar excesso")
  void deveLimitarConcorrenciaEnfileirarERejeitarExcesso() throws Exception {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    PdfGenerationLimiter limiter =
        new PdfGenerationLimiter(
            properties(1, 1, 7L), meterRegistry, MemoryRuntimeSnapshot::unavailable);
    CountDownLatch firstStarted = new CountDownLatch(1);
    CountDownLatch releaseFirst = new CountDownLatch(1);
    AtomicInteger currentExecutions = new AtomicInteger();
    AtomicInteger maximumExecutions = new AtomicInteger();
    ExecutorService executor = Executors.newFixedThreadPool(2);

    try {
      Future<String> first =
          executor.submit(
              () ->
                  limiter.execute(
                      () -> {
                        enterExecution(currentExecutions, maximumExecutions);
                        firstStarted.countDown();
                        try {
                          await(releaseFirst);
                          return "first";
                        } finally {
                          currentExecutions.decrementAndGet();
                        }
                      }));
      assertTrue(firstStarted.await(2L, TimeUnit.SECONDS));

      Future<String> second =
          executor.submit(
              () ->
                  limiter.execute(
                      () -> {
                        enterExecution(currentExecutions, maximumExecutions);
                        try {
                          return "second";
                        } finally {
                          currentExecutions.decrementAndGet();
                        }
                      }));
      awaitGauge(meterRegistry, PdfGenerationLimiter.QUEUE_SIZE_METRIC, 1.0d);

      assertThatThrownBy(() -> limiter.execute(() -> "rejected"))
          .isInstanceOf(PdfGenerationCapacityExceededException.class)
          .extracting("retryAfterSeconds")
          .isEqualTo(7L);

      releaseFirst.countDown();

      assertThat(first.get(2L, TimeUnit.SECONDS)).isEqualTo("first");
      assertThat(second.get(2L, TimeUnit.SECONDS)).isEqualTo("second");
      assertThat(maximumExecutions).hasValue(1);
      assertThat(meterRegistry.get(PdfGenerationLimiter.ACTIVE_METRIC).gauge().value()).isZero();
      assertThat(meterRegistry.get(PdfGenerationLimiter.QUEUE_SIZE_METRIC).gauge().value())
          .isZero();
      assertCounter(meterRegistry, "success", 2.0d);
      assertCounter(meterRegistry, "rejected", 1.0d);
      assertThat(meterRegistry.get(PdfGenerationLimiter.SLOT_HELD_METRIC).timer().count())
          .isEqualTo(2L);
    } finally {
      releaseFirst.countDown();
      executor.shutdownNow();
      assertTrue(executor.awaitTermination(2L, TimeUnit.SECONDS));
    }
  }

  @Test
  @DisplayName("Deve liberar slot e registrar erro quando geracao falha")
  void deveLiberarSlotERegistrarErroQuandoGeracaoFalha() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    PdfGenerationLimiter limiter =
        new PdfGenerationLimiter(
            properties(1, 0, 5L), meterRegistry, MemoryRuntimeSnapshot::unavailable);

    assertThatThrownBy(
            () ->
                limiter.execute(
                    () -> {
                      throw new IllegalArgumentException("pdf");
                    }))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("pdf");

    assertThat(limiter.execute(() -> "recovered")).isEqualTo("recovered");
    assertCounter(meterRegistry, "error", 1.0d);
    assertCounter(meterRegistry, "success", 1.0d);
    assertThat(meterRegistry.get(PdfGenerationLimiter.ACTIVE_METRIC).gauge().value()).isZero();
  }

  @Test
  @DisplayName("Deve medir aumento observado de RSS e memoria do container por PDF")
  void deveMedirAumentoObservadoDeMemoriaPorPdf() {
    SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
    AtomicInteger sample = new AtomicInteger();
    MemoryRuntimeProbe memoryRuntimeProbe =
        () -> sample.getAndIncrement() == 0 ? snapshot(100L, 1_000L) : snapshot(160L, 1_250L);
    PdfGenerationLimiter limiter =
        new PdfGenerationLimiter(properties(1, 0, 5L), meterRegistry, memoryRuntimeProbe);

    assertThat(limiter.execute(() -> "pdf")).isEqualTo("pdf");

    assertThat(
            meterRegistry
                .get(PdfGenerationLimiter.MEMORY_INCREASE_METRIC)
                .tag("scope", "java_process")
                .summary()
                .totalAmount())
        .isEqualTo(60.0d);
    assertThat(
            meterRegistry
                .get(PdfGenerationLimiter.MEMORY_INCREASE_METRIC)
                .tag("scope", "container")
                .summary()
                .totalAmount())
        .isEqualTo(250.0d);
    assertThat(meterRegistry.get(PdfGenerationLimiter.CONCURRENCY_LIMIT_METRIC).gauge().value())
        .isEqualTo(1.0d);
    assertThat(meterRegistry.get(PdfGenerationLimiter.QUEUE_CAPACITY_METRIC).gauge().value())
        .isZero();
  }

  private static PdfGenerationProperties properties(
      int maxConcurrency, int queueCapacity, long retryAfterSeconds) {
    PdfGenerationProperties properties = new PdfGenerationProperties();
    properties.setMaxConcurrency(maxConcurrency);
    properties.setQueueCapacity(queueCapacity);
    properties.setRetryAfterSeconds(retryAfterSeconds);
    return properties;
  }

  private static MemoryRuntimeSnapshot snapshot(long processRssBytes, long containerUsageBytes) {
    return new MemoryRuntimeSnapshot(
        OptionalLong.of(processRssBytes),
        OptionalLong.of(containerUsageBytes),
        OptionalLong.of(2_000L));
  }

  private static void enterExecution(
      AtomicInteger currentExecutions, AtomicInteger maximumExecutions) {
    int current = currentExecutions.incrementAndGet();
    maximumExecutions.accumulateAndGet(current, Math::max);
  }

  private static void await(CountDownLatch latch) {
    try {
      latch.await();
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Teste interrompido", exception);
    }
  }

  private static void awaitGauge(
      SimpleMeterRegistry meterRegistry, String metricName, double expected) {
    long deadline = System.nanoTime() + Duration.ofSeconds(2L).toNanos();
    double value;
    do {
      value = meterRegistry.get(metricName).gauge().value();
      if (Double.compare(value, expected) == 0) {
        return;
      }
      Thread.onSpinWait();
    } while (System.nanoTime() < deadline);
    assertThat(value).isEqualTo(expected);
  }

  private static void assertCounter(
      SimpleMeterRegistry meterRegistry, String result, double expected) {
    assertThat(
            meterRegistry
                .get(PdfGenerationLimiter.REQUESTS_METRIC)
                .tag("result", result)
                .counter()
                .count())
        .isEqualTo(expected);
  }
}
