package com.sistema_contabilidade.monitoring.memory.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

@DisplayName("LinuxMemoryRuntimeProbe unit tests")
class LinuxMemoryRuntimeProbeTest {

  @TempDir Path temporaryDirectory;

  @Test
  @DisplayName("Deve ler RSS e envelope cgroup v2 da aplicacao")
  void deveLerRssEEnvelopeCgroupV2() throws IOException {
    Path procStatus = temporaryDirectory.resolve("status");
    Path procCgroup = temporaryDirectory.resolve("cgroup");
    Path cgroupRoot = temporaryDirectory.resolve("sys-fs-cgroup");
    Path applicationCgroup = cgroupRoot.resolve("system.slice/sistema.service");
    Files.createDirectories(applicationCgroup);
    Files.writeString(procStatus, "Name:\tjava\nVmRSS:\t2048 kB\n");
    Files.writeString(procCgroup, "0::/system.slice/sistema.service\n");
    Files.writeString(applicationCgroup.resolve("memory.current"), "314572800\n");
    Files.writeString(applicationCgroup.resolve("memory.max"), "1073741824\n");

    MemoryRuntimeSnapshot snapshot =
        new LinuxMemoryRuntimeProbe(procStatus, procCgroup, cgroupRoot).snapshot();

    assertThat(snapshot.processRssBytes()).hasValue(2_097_152L);
    assertThat(snapshot.containerUsageBytes()).hasValue(314_572_800L);
    assertThat(snapshot.containerLimitBytes()).hasValue(1_073_741_824L);
  }

  @Test
  @DisplayName("Deve considerar memory.max igual a max como envelope ausente")
  void deveConsiderarLimiteMaxComoEnvelopeAusente() throws IOException {
    Path procStatus = temporaryDirectory.resolve("status-unlimited");
    Path procCgroup = temporaryDirectory.resolve("cgroup-unlimited");
    Path cgroupRoot = temporaryDirectory.resolve("cgroup-root-unlimited");
    Files.createDirectories(cgroupRoot);
    Files.writeString(procStatus, "VmRSS:\t1024 kB\n");
    Files.writeString(procCgroup, "0::/\n");
    Files.writeString(cgroupRoot.resolve("memory.current"), "1048576\n");
    Files.writeString(cgroupRoot.resolve("memory.max"), "max\n");

    MemoryRuntimeSnapshot snapshot =
        new LinuxMemoryRuntimeProbe(procStatus, procCgroup, cgroupRoot).snapshot();

    assertThat(snapshot.processRssBytes()).hasValue(1_048_576L);
    assertThat(snapshot.containerUsageBytes()).hasValue(1_048_576L);
    assertThat(snapshot.containerLimitBytes()).isEmpty();
  }

  @Test
  @DisplayName("Deve falhar fechado para caminho cgroup fora da raiz permitida")
  void deveFalharFechadoParaCaminhoForaDaRaiz() throws IOException {
    Path procStatus = temporaryDirectory.resolve("status-invalid");
    Path procCgroup = temporaryDirectory.resolve("cgroup-invalid");
    Path cgroupRoot = temporaryDirectory.resolve("cgroup-root-invalid");
    Files.createDirectories(cgroupRoot);
    Files.writeString(procStatus, "VmRSS:\t512 kB\n");
    Files.writeString(procCgroup, "0::/../../outside\n");

    MemoryRuntimeSnapshot snapshot =
        new LinuxMemoryRuntimeProbe(procStatus, procCgroup, cgroupRoot).snapshot();

    assertThat(snapshot.processRssBytes()).hasValue(524_288L);
    assertThat(snapshot.containerUsageBytes()).isEmpty();
    assertThat(snapshot.containerLimitBytes()).isEmpty();
  }
}
