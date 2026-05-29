package com.sistema_contabilidade.monitoring.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

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

  @Test
  @DisplayName("Deve expor razoes atuais de heap e metaspace dentro do intervalo esperado")
  void deveExporRazoesAtuaisDentroDoIntervaloEsperado() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    MemoryMonitoringService service = new MemoryMonitoringService(properties);

    assertThat(service.currentHeapUsageRatio()).isBetween(0.0d, 1.0d);
    assertThat(service.currentMetaspaceUsageRatio()).isBetween(0.0d, 1.0d);
  }

  @Test
  @DisplayName("Deve ignorar logging agendado quando monitoramento estiver desabilitado")
  void deveIgnorarLoggingAgendadoQuandoMonitoramentoEstiverDesabilitado() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setEnabled(false);
    properties.setScheduledLoggingEnabled(true);
    MemoryMonitoringService service = new MemoryMonitoringService(properties);

    assertDoesNotThrow(service::logWhenThresholdExceeded);
  }

  @Test
  @DisplayName("Deve permitir logging agendado sem alertas quando limites nao forem excedidos")
  void devePermitirLoggingAgendadoSemAlertasQuandoLimitesNaoForemExcedidos() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setEnabled(true);
    properties.setScheduledLoggingEnabled(true);
    properties.setHeapAlertThreshold(1.0d);
    properties.setMetaspaceAlertThreshold(1.0d);
    MemoryMonitoringService service = new MemoryMonitoringService(properties);

    assertDoesNotThrow(service::logWhenThresholdExceeded);
  }
}
