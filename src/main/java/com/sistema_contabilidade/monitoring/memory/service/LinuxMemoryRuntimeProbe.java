package com.sistema_contabilidade.monitoring.memory.service;

import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LinuxMemoryRuntimeProbe implements MemoryRuntimeProbe {

  private static final long BYTES_PER_KIBIBYTE = 1_024L;
  private static final long CGROUP_V1_UNLIMITED_THRESHOLD = 1L << 60;
  private static final String MEMORY_CONTROLLER = "memory";
  private static final String V2_CURRENT_FILE = "memory.current";
  private static final String V2_MAX_FILE = "memory.max";
  private static final String V1_CURRENT_FILE = "memory.usage_in_bytes";
  private static final String V1_MAX_FILE = "memory.limit_in_bytes";

  private final Path procStatusPath;
  private final Path procCgroupPath;
  private final Path cgroupRoot;

  @SuppressFBWarnings(
      value = "DMI_HARDCODED_ABSOLUTE_FILENAME",
      justification = "Linux procfs and cgroup pseudo-files use fixed absolute mount points.")
  @Autowired
  public LinuxMemoryRuntimeProbe() {
    this(Path.of("/proc/self/status"), Path.of("/proc/self/cgroup"), Path.of("/sys/fs/cgroup"));
  }

  LinuxMemoryRuntimeProbe(Path procStatusPath, Path procCgroupPath, Path cgroupRoot) {
    this.procStatusPath = procStatusPath;
    this.procCgroupPath = procCgroupPath;
    this.cgroupRoot = cgroupRoot;
  }

  @Override
  public MemoryRuntimeSnapshot snapshot() {
    OptionalLong processRssBytes = readProcessRssBytes();
    Optional<CgroupFiles> cgroupFiles = resolveCgroupFiles();
    if (cgroupFiles.isEmpty()) {
      return new MemoryRuntimeSnapshot(processRssBytes, OptionalLong.empty(), OptionalLong.empty());
    }
    CgroupFiles files = cgroupFiles.get();
    return new MemoryRuntimeSnapshot(
        processRssBytes,
        readNonNegativeLong(files.currentPath(), false),
        readNonNegativeLong(files.limitPath(), true));
  }

  private OptionalLong readProcessRssBytes() {
    try {
      Optional<String> rssLine =
          Files.readAllLines(procStatusPath).stream()
              .filter(line -> line.startsWith("VmRSS:"))
              .findFirst();
      if (rssLine.isEmpty()) {
        return OptionalLong.empty();
      }
      String[] parts = rssLine.get().substring("VmRSS:".length()).trim().split("\\s+");
      if (parts.length == 0) {
        return OptionalLong.empty();
      }
      long kibibytes = Long.parseLong(parts[0]);
      return OptionalLong.of(Math.multiplyExact(kibibytes, BYTES_PER_KIBIBYTE));
    } catch (IOException | ArithmeticException | NumberFormatException | SecurityException _) {
      return OptionalLong.empty();
    }
  }

  private Optional<CgroupFiles> resolveCgroupFiles() {
    List<String> cgroupLines;
    try {
      cgroupLines = Files.readAllLines(procCgroupPath);
    } catch (IOException | SecurityException _) {
      return resolveRootCgroupFiles();
    }

    Optional<CgroupFiles> v2 = resolveV2Files(cgroupLines);
    if (v2.isPresent()) {
      return v2;
    }
    Optional<CgroupFiles> v1 = resolveV1Files(cgroupLines);
    return v1.isPresent() ? v1 : resolveRootCgroupFiles();
  }

  private Optional<CgroupFiles> resolveV2Files(List<String> cgroupLines) {
    for (String line : cgroupLines) {
      String[] parts = line.split(":", 3);
      if (parts.length != 3 || !parts[1].isEmpty()) {
        continue;
      }
      Optional<Path> directory = safeResolve(cgroupRoot, parts[2]);
      if (directory.isPresent()) {
        CgroupFiles files =
            new CgroupFiles(
                directory.get().resolve(V2_CURRENT_FILE), directory.get().resolve(V2_MAX_FILE));
        if (files.exist()) {
          return Optional.of(files);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<CgroupFiles> resolveV1Files(List<String> cgroupLines) {
    for (String line : cgroupLines) {
      String[] parts = line.split(":", 3);
      if (parts.length != 3 || !hasMemoryController(parts[1])) {
        continue;
      }
      Optional<Path> memoryRoot = safeResolve(cgroupRoot, MEMORY_CONTROLLER);
      if (memoryRoot.isEmpty()) {
        return Optional.empty();
      }
      Optional<Path> directory = safeResolve(memoryRoot.get(), parts[2]);
      if (directory.isPresent()) {
        CgroupFiles files =
            new CgroupFiles(
                directory.get().resolve(V1_CURRENT_FILE), directory.get().resolve(V1_MAX_FILE));
        if (files.exist()) {
          return Optional.of(files);
        }
      }
    }
    return Optional.empty();
  }

  private Optional<CgroupFiles> resolveRootCgroupFiles() {
    CgroupFiles v2 =
        new CgroupFiles(cgroupRoot.resolve(V2_CURRENT_FILE), cgroupRoot.resolve(V2_MAX_FILE));
    if (v2.exist()) {
      return Optional.of(v2);
    }
    Path v1Root = cgroupRoot.resolve(MEMORY_CONTROLLER);
    CgroupFiles v1 = new CgroupFiles(v1Root.resolve(V1_CURRENT_FILE), v1Root.resolve(V1_MAX_FILE));
    return v1.exist() ? Optional.of(v1) : Optional.empty();
  }

  private Optional<Path> safeResolve(Path root, String cgroupPath) {
    String relativePath = cgroupPath == null ? "" : cgroupPath.replaceFirst("^/+", "");
    Path normalizedRoot = root.toAbsolutePath().normalize();
    Path resolved = normalizedRoot.resolve(relativePath).normalize();
    return resolved.startsWith(normalizedRoot) ? Optional.of(resolved) : Optional.empty();
  }

  private boolean hasMemoryController(String controllers) {
    for (String controller : controllers.split(",")) {
      if (MEMORY_CONTROLLER.equals(controller)) {
        return true;
      }
    }
    return false;
  }

  private OptionalLong readNonNegativeLong(Path path, boolean limit) {
    try {
      String rawValue = Files.readString(path).trim();
      if (rawValue.isEmpty() || "max".equalsIgnoreCase(rawValue)) {
        return OptionalLong.empty();
      }
      long value = Long.parseLong(rawValue);
      if (value < 0L || (limit && value >= CGROUP_V1_UNLIMITED_THRESHOLD)) {
        return OptionalLong.empty();
      }
      return OptionalLong.of(value);
    } catch (IOException | NumberFormatException | SecurityException _) {
      return OptionalLong.empty();
    }
  }

  private record CgroupFiles(Path currentPath, Path limitPath) {

    boolean exist() {
      return Files.isRegularFile(currentPath) && Files.isRegularFile(limitPath);
    }
  }
}
