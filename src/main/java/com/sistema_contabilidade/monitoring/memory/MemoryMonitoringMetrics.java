package com.sistema_contabilidade.monitoring.memory;

import com.sistema_contabilidade.monitoring.memory.service.MemoryMonitoringService;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.MeterBinder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MemoryMonitoringMetrics implements MeterBinder {

  private static final String BYTES_BASE_UNIT = "bytes";

  private final MemoryMonitoringService memoryMonitoringService;

  @Override
  public void bindTo(MeterRegistry registry) {
    Gauge.builder(
            "app.memory.heap.usage.ratio",
            memoryMonitoringService,
            MemoryMonitoringService::currentHeapUsageRatio)
        .description("Razao atual de uso do heap da JVM")
        .register(registry);
    Gauge.builder(
            "app.memory.metaspace.usage.ratio",
            memoryMonitoringService,
            MemoryMonitoringService::currentMetaspaceUsageRatio)
        .description("Razao atual de uso do Metaspace da JVM")
        .register(registry);
    Gauge.builder(
            "app.memory.process.rss.bytes",
            memoryMonitoringService,
            MemoryMonitoringService::currentProcessRssBytes)
        .description("Resident Set Size atual do processo Java em bytes")
        .baseUnit(BYTES_BASE_UNIT)
        .register(registry);
    Gauge.builder(
            "app.memory.container.usage.bytes",
            memoryMonitoringService,
            MemoryMonitoringService::currentContainerUsageBytes)
        .description("Memoria total usada pelo cgroup da aplicacao em bytes")
        .baseUnit(BYTES_BASE_UNIT)
        .register(registry);
    Gauge.builder(
            "app.memory.container.limit.bytes",
            memoryMonitoringService,
            MemoryMonitoringService::currentContainerLimitBytes)
        .description("Limite rigido de memoria do cgroup da aplicacao em bytes")
        .baseUnit(BYTES_BASE_UNIT)
        .register(registry);
    Gauge.builder(
            "app.memory.container.usage.ratio",
            memoryMonitoringService,
            MemoryMonitoringService::currentContainerUsageRatio)
        .description("Razao de uso do limite de memoria do cgroup")
        .register(registry);
    Gauge.builder(
            "app.memory.heap.max.to.container.ratio",
            memoryMonitoringService,
            MemoryMonitoringService::currentHeapMaxToContainerRatio)
        .description("Razao entre heap maximo da JVM e limite do cgroup")
        .register(registry);
    Gauge.builder(
            "app.memory.container.limit.configured",
            memoryMonitoringService,
            MemoryMonitoringService::currentContainerLimitConfigured)
        .description("Indica se existe limite cgroup finito: 1 configurado, 0 ausente")
        .register(registry);
  }
}
