package com.sistema_contabilidade.monitoring.memory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeProbe;
import com.sistema_contabilidade.monitoring.memory.service.MemoryRuntimeSnapshot;
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("MemoryEnvelopeValidator unit tests")
class MemoryEnvelopeValidatorTest {

  @Test
  @DisplayName("Deve rejeitar producao sem limite cgroup finito")
  void deveRejeitarProducaoSemLimiteCgroupFinito() {
    MemoryMonitoringProperties properties = properties(true, 0.50d);
    MemoryRuntimeProbe unavailableProbe = MemoryRuntimeSnapshot::unavailable;
    MemoryEnvelopeValidator validator = new MemoryEnvelopeValidator(properties, unavailableProbe);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("sem limite cgroup finito");
  }

  @Test
  @DisplayName("Deve rejeitar heap acima da proporcao permitida")
  void deveRejeitarHeapAcimaDaProporcaoPermitida() {
    long maxHeap = Runtime.getRuntime().maxMemory();
    MemoryMonitoringProperties properties = properties(true, 0.49d);
    MemoryRuntimeProbe probe = probe(maxHeap * 2L);
    MemoryEnvelopeValidator validator = new MemoryEnvelopeValidator(properties, probe);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Heap maximo excede");
  }

  @Test
  @DisplayName("Deve aceitar heap dentro do limite configurado")
  void deveAceitarHeapDentroDoLimiteConfigurado() {
    long maxHeap = Runtime.getRuntime().maxMemory();
    MemoryMonitoringProperties properties = properties(true, 0.50d);
    MemoryRuntimeProbe probe = probe(maxHeap * 3L);
    MemoryEnvelopeValidator validator = new MemoryEnvelopeValidator(properties, probe);

    assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Deve permitir ambiente local sem cgroup quando enforcement estiver desligado")
  void devePermitirAmbienteLocalSemCgroup() {
    MemoryMonitoringProperties properties = properties(false, 0.50d);
    MemoryRuntimeProbe unavailableProbe = MemoryRuntimeSnapshot::unavailable;
    MemoryEnvelopeValidator validator = new MemoryEnvelopeValidator(properties, unavailableProbe);

    assertThatCode(() -> validator.run(null)).doesNotThrowAnyException();
  }

  @Test
  @DisplayName("Deve rejeitar alerta de container maior ou igual ao critico")
  void deveRejeitarLimiaresInvertidos() {
    MemoryMonitoringProperties properties = properties(false, 0.50d);
    properties.setContainerAlertThreshold(0.80d);
    properties.setContainerCriticalThreshold(0.80d);
    MemoryEnvelopeValidator validator =
        new MemoryEnvelopeValidator(properties, MemoryRuntimeSnapshot::unavailable);

    assertThatThrownBy(() -> validator.run(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("alerta do container");
  }

  private MemoryMonitoringProperties properties(boolean enforced, double maxHeapRatio) {
    MemoryMonitoringProperties properties = new MemoryMonitoringProperties();
    properties.setEnvelopeEnforced(enforced);
    properties.setMaxHeapToContainerRatio(maxHeapRatio);
    return properties;
  }

  private MemoryRuntimeProbe probe(long containerLimit) {
    return () ->
        new MemoryRuntimeSnapshot(
            OptionalLong.of(100L), OptionalLong.of(200L), OptionalLong.of(containerLimit));
  }
}
