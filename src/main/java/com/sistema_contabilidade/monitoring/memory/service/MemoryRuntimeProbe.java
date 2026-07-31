package com.sistema_contabilidade.monitoring.memory.service;

@FunctionalInterface
public interface MemoryRuntimeProbe {

  MemoryRuntimeSnapshot snapshot();
}
