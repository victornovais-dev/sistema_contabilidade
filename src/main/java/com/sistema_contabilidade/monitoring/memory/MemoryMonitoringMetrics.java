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
  }
}
