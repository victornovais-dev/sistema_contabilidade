package com.sistema_contabilidade.monitoring.memory;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "app.memory-monitor")
@Getter
@Setter
public class MemoryMonitoringProperties {

  private boolean enabled = true;
  private boolean scheduledLoggingEnabled = false;
  private long fixedDelayMs = 60_000L;
  private double heapAlertThreshold = 0.85d;
  private double metaspaceAlertThreshold = 0.80d;
}
