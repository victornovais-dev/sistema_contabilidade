package com.sistema_contabilidade.monitoring.memory;

import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeProbe;
import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeSnapshot;
import java.util.OptionalLong;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class MemoryEnvelopeValidator implements ApplicationRunner {

  private static final long BYTES_PER_MEBIBYTE = 1_048_576L;

  private final MemoryMonitoringProperties properties;
  private final MemoryRuntimeProbe memoryRuntimeProbe;

  @Override
  public void run(ApplicationArguments arguments) {
    validateThresholds();
    MemoryRuntimeSnapshot snapshot = memoryRuntimeProbe.snapshot();
    long maxHeapBytes = Runtime.getRuntime().maxMemory();

    if (!properties.isEnvelopeEnforced()) {
      if (log.isInfoEnabled()) {
        log.info(
            "Memory envelope validation disabled; maxHeap={} MiB, containerLimit={} MiB",
            toMebibytes(maxHeapBytes),
            snapshot.containerLimitBytes().isPresent()
                ? toMebibytes(snapshot.containerLimitBytes().getAsLong())
                : "unavailable");
      }
      return;
    }

    long containerLimitBytes = requireContainerLimit(snapshot.containerLimitBytes(), maxHeapBytes);
    double heapToContainerRatio = ratio(maxHeapBytes, containerLimitBytes);
    if (heapToContainerRatio > properties.getMaxHeapToContainerRatio()) {
      throw new IllegalStateException(
          "Heap maximo excede o envelope configurado: "
              + toMebibytes(maxHeapBytes)
              + " MiB de heap para "
              + toMebibytes(containerLimitBytes)
              + " MiB de container ("
              + formatRatio(heapToContainerRatio)
              + " > "
              + formatRatio(properties.getMaxHeapToContainerRatio())
              + "). Ajuste -Xmx/-XX:MaxRAMPercentage ou o limite do runtime.");
    }

    if (log.isInfoEnabled()) {
      log.info(
          "Memory envelope validated; maxHeap={} MiB, containerLimit={} MiB, ratio={}",
          toMebibytes(maxHeapBytes),
          toMebibytes(containerLimitBytes),
          formatRatio(heapToContainerRatio));
    }
  }

  private void validateThresholds() {
    if (properties.getContainerAlertThreshold() >= properties.getContainerCriticalThreshold()) {
      throw new IllegalStateException(
          "Limite de alerta do container deve ser menor que o limite critico");
    }
  }

  private long requireContainerLimit(OptionalLong containerLimit, long maxHeapBytes) {
    if (containerLimit.isEmpty()) {
      throw new IllegalStateException(
          "Envelope de memoria habilitado sem limite cgroup finito. Configure --memory ou "
              + "systemd MemoryMax antes de iniciar a aplicacao. Heap detectado: "
              + toMebibytes(maxHeapBytes)
              + " MiB.");
    }
    return containerLimit.getAsLong();
  }

  private double ratio(long numerator, long denominator) {
    return denominator <= 0L ? 0.0d : (double) numerator / (double) denominator;
  }

  private long toMebibytes(long bytes) {
    return bytes <= 0L ? 0L : bytes / BYTES_PER_MEBIBYTE;
  }

  private String formatRatio(double value) {
    return String.format(java.util.Locale.ROOT, "%.1f%%", value * 100.0d);
  }
}
