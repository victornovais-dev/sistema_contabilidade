# Templates de Monitoramento de Memória — Spring Boot

## 1. application-memory.yml (Actuator + JVM Metrics)

```yaml
management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics,heapdump,threaddump,env
  endpoint:
    heapdump:
      enabled: true
    threaddump:
      enabled: true
  metrics:
    tags:
      application: ${spring.application.name}
    distribution:
      percentiles-histogram:
        jvm.memory.used: true

# Ativar métricas JVM detalhadas
spring:
  jmx:
    enabled: true
```

---

## 2. pom.xml — Dependências de Monitoramento

```xml
<!-- Actuator -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>

<!-- Micrometer para Prometheus (opcional) -->
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

---

## 3. JVM Flags Recomendados (jvm-flags.sh)

```bash
#!/bin/bash
# JVM flags para detectar e lidar com memory leaks

HEAP_MIN="-Xms512m"
HEAP_MAX="-Xmx2g"

# GC moderno e eficiente
GC_FLAGS="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"

# GC Logging (Java 11+)
GC_LOG="-Xlog:gc*:file=/var/log/app/gc.log:time,uptime:filecount=5,filesize=20m"

# Heap dump automático em OOM
OOM_FLAGS="-XX:+HeapDumpOnOutOfMemoryError -XX:HeapDumpPath=/var/dumps/heapdump.hprof"

# Ação em OOM (reiniciar processo)
OOM_ACTION="-XX:OnOutOfMemoryError='kill -9 %p'"

# Metaspace
METASPACE="-XX:MaxMetaspaceSize=256m"

# Diagnóstico adicional
DIAG="-XX:+PrintClassHistogramAfterFullGC -XX:+PrintGCDateStamps"

export JAVA_OPTS="$HEAP_MIN $HEAP_MAX $GC_FLAGS $GC_LOG $OOM_FLAGS $OOM_ACTION $METASPACE"
echo "JAVA_OPTS set: $JAVA_OPTS"
```

---

## 4. Script de Diagnóstico em Produção (diagnose-memory.sh)

```bash
#!/bin/bash
# Script de diagnóstico de memória para aplicação Spring Boot em produção

APP_NAME="${1:-spring-app}"
PID=$(pgrep -f "$APP_NAME")
TIMESTAMP=$(date +%Y%m%d_%H%M%S)
OUTPUT_DIR="/tmp/memory-diag-$TIMESTAMP"

mkdir -p "$OUTPUT_DIR"

if [ -z "$PID" ]; then
    echo "❌ Processo '$APP_NAME' não encontrado"
    exit 1
fi

echo "✅ Processo encontrado: PID $PID"
echo "📁 Salvando diagnóstico em: $OUTPUT_DIR"

echo "📊 1/5 — Coletando informações de memória JVM..."
jcmd "$PID" VM.native_memory summary > "$OUTPUT_DIR/native_memory.txt" 2>&1

echo "📊 2/5 — Coletando histograma de objetos (sem full GC)..."
jcmd "$PID" GC.class_histogram > "$OUTPUT_DIR/class_histogram.txt" 2>&1

echo "📊 3/5 — Tirando heap dump..."
jcmd "$PID" GC.heap_dump "$OUTPUT_DIR/heapdump.hprof"

echo "📊 4/5 — Tirando thread dump..."
jstack "$PID" > "$OUTPUT_DIR/threaddump.txt" 2>&1

echo "📊 5/5 — Coletando estatísticas de GC..."
jstat -gcutil "$PID" 1000 10 > "$OUTPUT_DIR/gcstat.txt" 2>&1

echo ""
echo "✅ Diagnóstico completo! Arquivos em: $OUTPUT_DIR"
echo ""
echo "Próximos passos:"
echo "  - Analisar heapdump.hprof no Eclipse Memory Analyzer (MAT)"
echo "    Download: https://eclipse.dev/mat/"
echo "  - Ver top objetos: head -50 $OUTPUT_DIR/class_histogram.txt"
echo "  - Ver threads presas: grep -A5 'BLOCKED' $OUTPUT_DIR/threaddump.txt"
```

---

## 5. Bean de Alerta de Memória (MemoryLeakDiagnostics.java)

```java
package com.example.diagnostics;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.actuate.endpoint.annotation.Endpoint;
import org.springframework.boot.actuate.endpoint.annotation.ReadOperation;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.lang.management.*;
import java.util.*;

@Component
@Endpoint(id = "memory-health")
public class MemoryLeakDiagnostics {

    private static final Logger log = LoggerFactory.getLogger(MemoryLeakDiagnostics.class);

    // Alertar quando heap usado passar de 85%
    private static final double HEAP_ALERT_THRESHOLD = 0.85;
    
    // Alertar quando Metaspace passar de 80%
    private static final double METASPACE_ALERT_THRESHOLD = 0.80;

    @Scheduled(fixedDelay = 60_000) // a cada 1 minuto
    public void monitorMemory() {
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        // Heap
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        double heapRatio = (double) heap.getUsed() / heap.getMax();
        
        if (heapRatio > HEAP_ALERT_THRESHOLD) {
            log.warn("⚠️  ALERTA DE MEMÓRIA: Heap em {:.1f}% ({} MB usados de {} MB máx)",
                heapRatio * 100,
                heap.getUsed() / 1_048_576,
                heap.getMax() / 1_048_576
            );
        } else {
            log.info("✅ Heap OK: {:.1f}% ({} MB / {} MB)",
                heapRatio * 100,
                heap.getUsed() / 1_048_576,
                heap.getMax() / 1_048_576
            );
        }
        
        // Metaspace
        ManagementFactory.getMemoryPoolMXBeans().stream()
            .filter(p -> p.getName().contains("Metaspace"))
            .findFirst()
            .ifPresent(metaspace -> {
                MemoryUsage usage = metaspace.getUsage();
                if (usage.getMax() > 0) {
                    double ratio = (double) usage.getUsed() / usage.getMax();
                    if (ratio > METASPACE_ALERT_THRESHOLD) {
                        log.warn("⚠️  ALERTA METASPACE: {:.1f}% cheio — possível ClassLoader leak!",
                            ratio * 100);
                    }
                }
            });
    }

    @ReadOperation
    public Map<String, Object> memoryReport() {
        Map<String, Object> report = new LinkedHashMap<>();
        MemoryMXBean memoryBean = ManagementFactory.getMemoryMXBean();
        
        MemoryUsage heap = memoryBean.getHeapMemoryUsage();
        report.put("heap_used_mb", heap.getUsed() / 1_048_576);
        report.put("heap_max_mb", heap.getMax() / 1_048_576);
        report.put("heap_percent", String.format("%.1f%%", (double) heap.getUsed() / heap.getMax() * 100));
        
        List<Map<String, Object>> pools = new ArrayList<>();
        for (MemoryPoolMXBean pool : ManagementFactory.getMemoryPoolMXBeans()) {
            Map<String, Object> poolInfo = new LinkedHashMap<>();
            poolInfo.put("name", pool.getName());
            MemoryUsage u = pool.getUsage();
            poolInfo.put("used_mb", u.getUsed() / 1_048_576);
            poolInfo.put("max_mb", u.getMax() / 1_048_576);
            pools.add(poolInfo);
        }
        report.put("memory_pools", pools);
        
        return report;
    }
}
```

---

## 6. Como analisar o Heap Dump com Eclipse MAT

1. **Baixar Eclipse MAT**: https://eclipse.dev/mat/
2. Abrir o `.hprof` gerado
3. Ir em **"Leak Suspects Report"** — MAT aponta automaticamente os maiores suspeitos
4. Verificar **"Dominator Tree"** — mostra os objetos que mais retêm memória
5. Verificar **"Top Consumers"** — mostra quais classes têm mais instâncias

**Principais indicadores de leak:**
- Uma classe com milhares/milhões de instâncias não esperadas
- `char[]` ou `byte[]` dominando o heap (geralmente Strings ou dados não liberados)
- Referências a `ClassLoader` em pools de objetos
