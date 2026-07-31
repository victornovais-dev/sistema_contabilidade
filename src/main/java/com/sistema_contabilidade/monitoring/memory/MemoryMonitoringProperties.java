package com.sistema_contabilidade.monitoring.memory;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "app.memory-monitor")
@Validated
@Getter
@Setter
public class MemoryMonitoringProperties {

  private static final String MIN_RATIO = "0.01";
  private static final String MAX_RATIO = "1.0";

  private boolean enabled = true;
  private boolean scheduledLoggingEnabled = false;

  @Positive private long fixedDelayMs = 60_000L;

  @DecimalMin(MIN_RATIO)
  @DecimalMax(MAX_RATIO)
  private double heapAlertThreshold = 0.70d;

  @DecimalMin(MIN_RATIO)
  @DecimalMax(MAX_RATIO)
  private double metaspaceAlertThreshold = 0.80d;

  @DecimalMin(MIN_RATIO)
  @DecimalMax(MAX_RATIO)
  private double containerAlertThreshold = 0.70d;

  @DecimalMin(MIN_RATIO)
  @DecimalMax(MAX_RATIO)
  private double containerCriticalThreshold = 0.80d;

  @DecimalMin(MIN_RATIO)
  @DecimalMax(MAX_RATIO)
  private double maxHeapToContainerRatio = 0.50d;

  private boolean envelopeEnforced = false;
}
