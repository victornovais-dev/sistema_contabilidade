package com.sistema_contabilidade.relatorio.service;

import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeProbe;
import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeSnapshot;
import com.sistema_contabilidade.relatorio.config.PdfGenerationProperties;
import com.sistema_contabilidade.relatorio.exception.PdfGenerationCapacityExceededException;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

@Component
public final class PdfGenerationLimiter {

  public static final String ACTIVE_METRIC = "app.pdf.concurrent.active";
  public static final String CONCURRENCY_LIMIT_METRIC = "app.pdf.concurrent.limit";
  public static final String QUEUE_SIZE_METRIC = "app.pdf.queue.size";
  public static final String QUEUE_CAPACITY_METRIC = "app.pdf.queue.capacity";
  public static final String REQUESTS_METRIC = "app.pdf.requests";
  public static final String SLOT_HELD_METRIC = "app.pdf.slot.held";
  public static final String MEMORY_INCREASE_METRIC = "app.pdf.memory.increase";

  private static final String RESULT_TAG = "result";
  private static final String SCOPE_TAG = "scope";
  private static final String PROCESS_SCOPE = "java_process";
  private static final String CONTAINER_SCOPE = "container";

  private final Semaphore admissionSlots;
  private final Semaphore executionSlots;
  private final AtomicInteger active = new AtomicInteger();
  private final AtomicInteger queued = new AtomicInteger();
  private final long retryAfterSeconds;
  private final MemoryRuntimeProbe memoryRuntimeProbe;
  private final Counter successCounter;
  private final Counter errorCounter;
  private final Counter rejectedCounter;
  private final Counter interruptedCounter;
  private final Timer slotHeldTimer;
  private final DistributionSummary processMemoryIncrease;
  private final DistributionSummary containerMemoryIncrease;

  public PdfGenerationLimiter(
      PdfGenerationProperties properties,
      MeterRegistry meterRegistry,
      MemoryRuntimeProbe memoryRuntimeProbe) {
    Objects.requireNonNull(properties, "properties");
    Objects.requireNonNull(meterRegistry, "meterRegistry");
    this.memoryRuntimeProbe = Objects.requireNonNull(memoryRuntimeProbe, "memoryRuntimeProbe");
    int maxConcurrency = properties.getMaxConcurrency();
    int queueCapacity = properties.getQueueCapacity();
    this.retryAfterSeconds = properties.getRetryAfterSeconds();
    this.admissionSlots = new Semaphore(Math.addExact(maxConcurrency, queueCapacity), true);
    this.executionSlots = new Semaphore(maxConcurrency, true);
    this.successCounter = requestCounter(meterRegistry, "success");
    this.errorCounter = requestCounter(meterRegistry, "error");
    this.rejectedCounter = requestCounter(meterRegistry, "rejected");
    this.interruptedCounter = requestCounter(meterRegistry, "interrupted");
    this.slotHeldTimer =
        Timer.builder(SLOT_HELD_METRIC)
            .description("Time a PDF generation execution slot remains held")
            .register(meterRegistry);
    this.processMemoryIncrease = memoryIncreaseSummary(meterRegistry, PROCESS_SCOPE);
    this.containerMemoryIncrease = memoryIncreaseSummary(meterRegistry, CONTAINER_SCOPE);
    registerGauges(meterRegistry, maxConcurrency, queueCapacity);
  }

  public <T> T execute(Supplier<T> task) {
    Objects.requireNonNull(task, "task");
    if (!admissionSlots.tryAcquire()) {
      rejectedCounter.increment();
      throw new PdfGenerationCapacityExceededException(retryAfterSeconds);
    }

    boolean executionSlotAcquired = false;
    try {
      executionSlotAcquired = acquireExecutionSlot();
      return executeWithSlot(task);
    } finally {
      if (executionSlotAcquired) {
        executionSlots.release();
      }
      admissionSlots.release();
    }
  }

  private boolean acquireExecutionSlot() {
    try {
      if (executionSlots.tryAcquire(0L, TimeUnit.NANOSECONDS)) {
        return true;
      }
      queued.incrementAndGet();
      try {
        executionSlots.acquire();
        return true;
      } finally {
        queued.decrementAndGet();
      }
    } catch (InterruptedException exception) {
      Thread.currentThread().interrupt();
      interruptedCounter.increment();
      throw new IllegalStateException("Geracao de PDF interrompida", exception);
    }
  }

  private <T> T executeWithSlot(Supplier<T> task) {
    active.incrementAndGet();
    MemoryRuntimeSnapshot before = safeSnapshot();
    long startedAt = System.nanoTime();
    try {
      T result = task.get();
      successCounter.increment();
      return result;
    } catch (RuntimeException exception) {
      errorCounter.increment();
      throw exception;
    } finally {
      long heldNanos = System.nanoTime() - startedAt;
      MemoryRuntimeSnapshot after = safeSnapshot();
      recordMemoryIncrease(before, after);
      slotHeldTimer.record(heldNanos, TimeUnit.NANOSECONDS);
      active.decrementAndGet();
    }
  }

  private MemoryRuntimeSnapshot safeSnapshot() {
    try {
      return memoryRuntimeProbe.snapshot();
    } catch (RuntimeException _) {
      return MemoryRuntimeSnapshot.unavailable();
    }
  }

  private void recordMemoryIncrease(MemoryRuntimeSnapshot before, MemoryRuntimeSnapshot after) {
    recordPositiveIncrease(
        before.processRssBytes(), after.processRssBytes(), processMemoryIncrease);
    recordPositiveIncrease(
        before.containerUsageBytes(), after.containerUsageBytes(), containerMemoryIncrease);
  }

  private void recordPositiveIncrease(
      OptionalLong before, OptionalLong after, DistributionSummary summary) {
    if (before.isEmpty() || after.isEmpty()) {
      return;
    }
    long increase = Math.max(0L, after.getAsLong() - before.getAsLong());
    summary.record(increase);
  }

  private void registerGauges(MeterRegistry meterRegistry, int maxConcurrency, int queueCapacity) {
    Gauge.builder(ACTIVE_METRIC, active, AtomicInteger::get)
        .description("PDF generations currently holding execution slots")
        .register(meterRegistry);
    Gauge.builder(QUEUE_SIZE_METRIC, queued, AtomicInteger::get)
        .description("PDF generations currently waiting for execution slots")
        .register(meterRegistry);
    Gauge.builder(CONCURRENCY_LIMIT_METRIC, () -> maxConcurrency)
        .description("Configured maximum concurrent PDF generations")
        .register(meterRegistry);
    Gauge.builder(QUEUE_CAPACITY_METRIC, () -> queueCapacity)
        .description("Configured maximum PDF generation queue size")
        .register(meterRegistry);
  }

  private Counter requestCounter(MeterRegistry meterRegistry, String result) {
    return Counter.builder(REQUESTS_METRIC)
        .description("PDF generation request outcomes")
        .tag(RESULT_TAG, result)
        .register(meterRegistry);
  }

  private DistributionSummary memoryIncreaseSummary(MeterRegistry meterRegistry, String scope) {
    return DistributionSummary.builder(MEMORY_INCREASE_METRIC)
        .baseUnit("bytes")
        .description("Observed memory increase while holding a PDF generation slot")
        .tag(SCOPE_TAG, scope)
        .register(meterRegistry);
  }
}
