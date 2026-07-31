package com.sistema_contabilidade.monitoring.memory.service;

import java.util.Objects;
import java.util.OptionalLong;

public record MemoryRuntimeSnapshot(
    OptionalLong processRssBytes,
    OptionalLong containerUsageBytes,
    OptionalLong containerLimitBytes) {

  public MemoryRuntimeSnapshot {
    Objects.requireNonNull(processRssBytes, "processRssBytes");
    Objects.requireNonNull(containerUsageBytes, "containerUsageBytes");
    Objects.requireNonNull(containerLimitBytes, "containerLimitBytes");
  }

  public static MemoryRuntimeSnapshot unavailable() {
    return new MemoryRuntimeSnapshot(
        OptionalLong.empty(), OptionalLong.empty(), OptionalLong.empty());
  }
}
