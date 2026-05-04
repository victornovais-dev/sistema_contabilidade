package com.sistema_contabilidade.monitoring.memory.service;

import com.sistema_contabilidade.monitoring.memory.MemoryMonitoringProperties;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.MemoryPoolMXBean;
import java.lang.management.MemoryUsage;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class MemoryMonitoringService {

  private static final long BYTES_PER_MEGABYTE = 1_048_576L;
  private static final String METASPACE_NAME = "Metaspace";
  private static final long ZERO_BYTES = 0L;

  private final MemoryMonitoringProperties properties;

  public Map<String, Object> createReport() {
    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    MemoryUsage nonHeapUsage = memoryBean.getNonHeapMemoryUsage();
    Optional<MemoryUsage> metaspaceUsage = findMetaspaceUsage();

    List<String> warnings = buildWarnings(heapUsage, metaspaceUsage);
    Map<String, Object> report = new LinkedHashMap<>();
    report.put("capturedAt", Instant.now().toString());
    report.put("monitoringEnabled", properties.isEnabled());
    report.put("scheduledLoggingEnabled", properties.isScheduledLoggingEnabled());
    report.put("thresholds", buildThresholds());
    report.put("heap", buildAreaReport("heap", heapUsage));
    report.put("nonHeap", buildAreaReport("nonHeap", nonHeapUsage));
    report.put("metaspace", buildMetaspaceReport(metaspaceUsage));
    report.put("warnings", warnings);
    report.put("memoryPools", buildPoolReports());
    return report;
  }

  public double currentHeapUsageRatio() {
    return usageRatio(ManagementFactory.getMemoryMXBean().getHeapMemoryUsage());
  }

  public double currentMetaspaceUsageRatio() {
    return findMetaspaceUsage().map(this::usageRatio).orElse(0.0d);
  }

  @Scheduled(fixedDelayString = "${app.memory-monitor.fixed-delay-ms:60000}")
  public void logWhenThresholdExceeded() {
    if (!properties.isEnabled() || !properties.isScheduledLoggingEnabled()) {
      return;
    }

    MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
    MemoryUsage heapUsage = memoryBean.getHeapMemoryUsage();
    Optional<MemoryUsage> metaspaceUsage = findMetaspaceUsage();
    List<String> warnings = buildWarnings(heapUsage, metaspaceUsage);

    if (warnings.isEmpty()) {
      return;
    }
    if (log.isWarnEnabled()) {
      log.warn(
          "MEMORY ALERT - heap={} MB/{} MB ({}), metaspace={}, avisos={}",
          toMegabytes(heapUsage.getUsed()),
          toMegabytes(resolveMaxBytes(heapUsage)),
          formatRatio(usageRatio(heapUsage)),
          metaspaceUsage
              .map(
                  usage ->
                      toMegabytes(usage.getUsed())
                          + " MB/"
                          + toMegabytes(resolveMaxBytes(usage))
                          + " MB ("
                          + formatRatio(usageRatio(usage))
                          + ")")
              .orElse("indisponivel"),
          String.join(" | ", warnings));
    }
  }

  private Map<String, Object> buildThresholds() {
    Map<String, Object> thresholds = new LinkedHashMap<>();
    thresholds.put("heapAlertThreshold", properties.getHeapAlertThreshold());
    thresholds.put("metaspaceAlertThreshold", properties.getMetaspaceAlertThreshold());
    return thresholds;
  }

  private Map<String, Object> buildMetaspaceReport(Optional<MemoryUsage> metaspaceUsage) {
    if (metaspaceUsage.isEmpty()) {
      Map<String, Object> unavailable = new LinkedHashMap<>();
      unavailable.put("name", METASPACE_NAME);
      unavailable.put("available", false);
      return unavailable;
    }

    Map<String, Object> report = buildAreaReport(METASPACE_NAME, metaspaceUsage.get());
    report.put("available", true);
    return report;
  }

  private List<Map<String, Object>> buildPoolReports() {
    return ManagementFactory.getMemoryPoolMXBeans().stream().map(this::buildPoolReport).toList();
  }

  private Map<String, Object> buildPoolReport(MemoryPoolMXBean pool) {
    return buildAreaReport(pool.getName(), pool.getUsage());
  }

  private Map<String, Object> buildAreaReport(String name, MemoryUsage usage) {
    Map<String, Object> area = new LinkedHashMap<>();
    long resolvedMaxBytes = resolveMaxBytes(usage);
    area.put("name", name);
    area.put("usedBytes", usage.getUsed());
    area.put("committedBytes", usage.getCommitted());
    area.put("maxBytes", resolvedMaxBytes);
    area.put("usedMb", toMegabytes(usage.getUsed()));
    area.put("committedMb", toMegabytes(usage.getCommitted()));
    area.put("maxMb", toMegabytes(resolvedMaxBytes));
    area.put("usageRatio", usageRatio(usage));
    return area;
  }

  private List<String> buildWarnings(MemoryUsage heapUsage, Optional<MemoryUsage> metaspaceUsage) {
    List<String> warnings = new ArrayList<>();
    double heapRatio = usageRatio(heapUsage);
    if (heapRatio >= properties.getHeapAlertThreshold()) {
      warnings.add(
          "Heap acima do limite configurado: "
              + formatRatio(heapRatio)
              + " usado (limite "
              + formatRatio(properties.getHeapAlertThreshold())
              + ")");
    }

    metaspaceUsage.ifPresent(
        usage -> {
          double metaspaceRatio = usageRatio(usage);
          if (metaspaceRatio >= properties.getMetaspaceAlertThreshold()) {
            warnings.add(
                "Metaspace acima do limite configurado: "
                    + formatRatio(metaspaceRatio)
                    + " usado (limite "
                    + formatRatio(properties.getMetaspaceAlertThreshold())
                    + ")");
          }
        });
    return warnings;
  }

  private Optional<MemoryUsage> findMetaspaceUsage() {
    return ManagementFactory.getMemoryPoolMXBeans().stream()
        .filter(pool -> pool.getName().contains(METASPACE_NAME))
        .map(MemoryPoolMXBean::getUsage)
        .findFirst();
  }

  private double usageRatio(MemoryUsage usage) {
    long maxBytes = resolveMaxBytes(usage);
    if (maxBytes <= ZERO_BYTES) {
      return 0.0d;
    }
    return Math.min(1.0d, (double) usage.getUsed() / (double) maxBytes);
  }

  private long resolveMaxBytes(MemoryUsage usage) {
    return usage.getMax() > 0L ? usage.getMax() : usage.getCommitted();
  }

  private long toMegabytes(long bytes) {
    return bytes <= 0L ? 0L : bytes / BYTES_PER_MEGABYTE;
  }

  private String formatRatio(double ratio) {
    return String.format(Locale.ROOT, "%.1f%%", ratio * 100.0d);
  }
}
