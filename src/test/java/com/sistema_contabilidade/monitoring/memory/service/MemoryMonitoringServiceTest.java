package com.sistema_contabilidade.monitoring.memory.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import com.sistema_contabilidade.monitoring.memory.MemoryMonitoringProperties;
import java.util.List;
import java.util.Map;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryMonitoringService unit tests")
class MemoryMonitoringServiceTest {

  @Test
  @DisplayName("Deve gerar relatorio com secoes principais de memoria")
  void deveGerarRelatorioComSecoesPrincipaisDeMemoria() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    MemoryMonitoringService service = service(properties, 100L, 400L, 1_000L);

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
            "runtimeEnvelope",
            "warnings",
            "memoryPools");
    assertThat(report.get("memoryPools")).isInstanceOf(List.class);
    assertThat(report.get("runtimeEnvelope"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.MAP)
        .containsEntry("containerUsageRatio", 0.4d);
  }

  @Test
  @DisplayName("Deve adicionar alerta quando limites sao configurados abaixo do uso atual")
  void deveAdicionarAlertaQuandoLimitesSaoConfiguradosAbaixoDoUsoAtual() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setHeapAlertThreshold(0.0d);
    properties.setMetaspaceAlertThreshold(0.0d);
    MemoryMonitoringService service = service(properties, 100L, 400L, 1_000L);

    Map<String, Object> report = service.createReport();

    assertThat(report.get("warnings"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .isNotEmpty();
  }

  @Test
  @DisplayName("Deve expor razoes atuais de heap e metaspace dentro do intervalo esperado")
  void deveExporRazoesAtuaisDentroDoIntervaloEsperado() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    MemoryMonitoringService service = service(properties, 100L, 400L, 1_000L);

    assertThat(service.currentHeapUsageRatio()).isBetween(0.0d, 1.0d);
    assertThat(service.currentMetaspaceUsageRatio()).isBetween(0.0d, 1.0d);
    assertThat(service.currentProcessRssBytes()).isEqualTo(100.0d);
    assertThat(service.currentContainerUsageBytes()).isEqualTo(400.0d);
    assertThat(service.currentContainerLimitBytes()).isEqualTo(1_000.0d);
    assertThat(service.currentContainerUsageRatio()).isEqualTo(0.4d);
    assertThat(service.currentContainerLimitConfigured()).isEqualTo(1.0d);
  }

  @Test
  @DisplayName("Deve ignorar logging agendado quando monitoramento estiver desabilitado")
  void deveIgnorarLoggingAgendadoQuandoMonitoramentoEstiverDesabilitado() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setEnabled(false);
    properties.setScheduledLoggingEnabled(true);
    MemoryMonitoringService service = service(properties, 100L, 400L, 1_000L);

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
    properties.setContainerAlertThreshold(0.90d);
    properties.setContainerCriticalThreshold(1.0d);
    MemoryMonitoringService service = service(properties, 100L, 400L, Long.MAX_VALUE / 2L);

    assertDoesNotThrow(service::logWhenThresholdExceeded);
  }

  @Test
  @DisplayName("Deve alertar quando uso do container ultrapassar limite critico")
  void deveAlertarQuandoUsoDoContainerUltrapassarLimiteCritico() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setHeapAlertThreshold(1.0d);
    properties.setMetaspaceAlertThreshold(1.0d);
    MemoryMonitoringService service = service(properties, 700L, 850L, 1_000L);

    Map<String, Object> report = service.createReport();

    assertThat(report.get("warnings"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .anyMatch(message -> message.toString().contains("limite critico"));
  }

  @Test
  @DisplayName("Deve sinalizar ausencia de limite quando envelope for obrigatorio")
  void deveSinalizarAusenciaDeLimiteQuandoEnvelopeForObrigatorio() {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setEnvelopeEnforced(true);
    MemoryRuntimeProbe unavailableProbe = MemoryRuntimeSnapshot::unavailable;
    MemoryMonitoringService service = new MemoryMonitoringService(properties, unavailableProbe);

    Map<String, Object> report = service.createReport();

    assertThat(report.get("warnings"))
        .asInstanceOf(org.assertj.core.api.InstanceOfAssertFactories.LIST)
        .contains("Limite finito do container indisponivel");
    assertThat(service.currentContainerLimitConfigured()).isZero();
  }

  private MemoryMonitoringService service(
      MemoryMonitoringProperties properties, long processRss, long containerUsage, long limit) {
    MemoryRuntimeProbe probe =
        () ->
            new MemoryRuntimeSnapshot(
                OptionalLong.of(processRss),
                OptionalLong.of(containerUsage),
                OptionalLong.of(limit));
    return new MemoryMonitoringService(properties, probe);
  }
}
