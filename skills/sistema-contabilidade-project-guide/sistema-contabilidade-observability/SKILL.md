---
name: sistema-contabilidade-observability
description: Use for sistema_contabilidade performance diagnostics, query count, N+1, Micrometer metrics, Prometheus, Grafana, Actuator, HTTP timing, memory monitoring, Redis and cache strategy.
---

# Sistema Contabilidade Observability and Performance

Use this skill when the task mentions query count, N+1, Prometheus, Grafana, Actuator, timing headers, memory pressure, slow requests, metrics, Redis or caching.

## First Read

1. Read `references/observability.md`.
2. Inspect only the affected monitoring/cache files.
3. If the task touches item list performance, also read `skills/sistema-contabilidade-items/SKILL.md`.
4. If it touches deployment/Redis config, also read `skills/sistema-contabilidade-deploy-config/SKILL.md`.

## Main Files

- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountFilter.java`
- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountContext.java`
- `src/main/java/com/sistema_contabilidade/monitoring/query/QueryCountStatementInspector.java`
- `src/main/java/com/sistema_contabilidade/monitoring/http/RequestTimingFilter.java`
- `src/main/java/com/sistema_contabilidade/monitoring/RequestMonitoringPathUtils.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/service/MemoryMonitoringService.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringMetrics.java`
- `src/main/java/com/sistema_contabilidade/monitoring/memory/MemoryMonitoringProperties.java`
- `src/test/java/com/sistema_contabilidade/monitoring/query/QueryCountAuditIntegrationTest.java`
- `src/test/java/com/sistema_contabilidade/monitoring/query/QueryCountPrometheusIntegrationTest.java`
- `observability/README.md`
- `observability/docker-compose.yml`
- Prometheus/Grafana provisioning files under `observability/`

## Workflow

1. Identify if the issue is SQL count, request timing, memory, cache or dashboard config.
2. Inspect the monitoring filter/context/metrics code for that area.
3. Preserve ignored paths for assets, favicon and `/actuator`.
4. For query count budgets, update/add focused tests.
5. For dashboards/alerts, inspect `observability/` provisioning.
6. For Redis/cache changes, verify whether Spring cache is actually using Caffeine or Redis.

## Validation

- Run focused monitoring tests.
- Run Prometheus integration tests if `/actuator/prometheus` changes.
- Verify headers such as `X-Query-Count`, `X-App-Time-Ms` and `Server-Timing` when relevant.
