package com.sistema_contabilidade.monitoring.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.sistema_contabilidade.monitoring.memory.MemoryMonitoringProperties;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryMonitoringService unit tests")
class MemoryMonitoringServiceTest {

  @Test
  @DisplayName("Deve gerar relatorio com secoes principais de memoria")
  void deveGerarRelatorioComSecoesPrincipaisDeMemoria() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    MemoryMonitoringService service = new MemoryMonitoringService(properties);

    Map<String, Object> report = service.createReport();

    assertThat(report)
        .containsKeys(
            "capturedAt",
            "monitoringEnabled",
            "scheduledLoggingEnabled",
            "thresholds",
            "heap",
            "nonHeap",
            "metaspace",
            "warnings",
            "memoryPools");
    assertThat(report.get("memoryPools")).isInstanceOf(List.class);
  }

  @Test
  @DisplayName("Deve adicionar alerta quando limites sao configurados abaixo do uso atual")
  void deveAdicionarAlertaQuandoLimitesSaoConfiguradosAbaixoDoUsoAtual() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setHeapAlertThreshold(0.0d);
    properties.setMetaspaceAlertThreshold(0.0d);
    MemoryMonitoringService service = new MemoryMonitoringService(properties);

    Map<String, Object> report = service.createReport();

    assertThat(report.get("warnings"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .isNotEmpty();
  }
}
