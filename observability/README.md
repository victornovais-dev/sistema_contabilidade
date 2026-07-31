# Observability

## Local stack

Start the application on port `8080`, then run:

```powershell
docker compose -f observability/docker-compose.yml up -d
```

Open:

```text
http://localhost:3001
```

Default local credentials:

```text
admin / admin
```

Set `GRAFANA_ADMIN_USER` and `GRAFANA_ADMIN_PASSWORD` before starting Docker Compose to override
the local defaults.

## Query count dashboard

Grafana dashboard file:

```text
observability/grafana/dashboards/query-count-dashboard.json
```

Dashboard provider:

```text
observability/grafana/provisioning/dashboards/dashboards.yaml
```

Datasource provider:

```text
observability/grafana/provisioning/datasources/prometheus.yaml
```

The dashboard shows:

- worst SQL query count in the selected time range
- top routes by maximum query count
- requests per second by route
- average queries per request by route
- route ranking table
- routes above the `15` query threshold

## Query count alerts

The application exports per-route SQL query counts through Micrometer at:

```text
/actuator/prometheus
```

Prometheus rule file:

```text
observability/prometheus/rules/query-count-alerts.yml
```

Grafana alert provisioning file:

```text
observability/grafana/provisioning/alerting/query-count-alerts.yaml
```

The alert fires when any API route records more than 15 SQL queries in a request within the last
5 minutes. The metric is tagged by `method` and `uri`.

For Grafana provisioning, set `PROMETHEUS_DS_UID` to the UID of the Prometheus datasource.

## Prometheus scrape config

Local Prometheus config:

```text
observability/prometheus/prometheus.yml
```

It scrapes:

```text
host.docker.internal:8080/actuator/prometheus
```

## Multi-AZ database and Valkey

The ALB must probe only:

```text
/actuator/health/alb
```

This public health group contains only `ping` and the writer datasource. Reader or Valkey failures
must not remove an EC2 instance from the ALB because both dependencies have safe fallbacks.

Operational metrics use fixed, low-cardinality tags:

| Micrometer metric | Tags | Meaning |
|---|---|---|
| `app.db.route.total` | `route`, `reason` | Writer/reader route decisions |
| `app.db.reader.connection.failures` | none | Reader acquisition failures |
| `app.db.reader.circuit.state` | none | `0` closed, `0.5` probe, `1` open |
| `app.db.sticky.total` | `result` | Sticky checks and renewals |
| `app.rate_limit.total` | `backend`, `result` | Valkey/local rate-limit decisions |
| `app.valkey.operation.errors` | `operation` | Failed Valkey operations |
| `app.relatorio.resumo.cache.total` | `result` | Report-cache hit/miss/bypass/error |
| `app.memory.heap.usage.ratio` | none | JVM heap used divided by maximum heap |
| `app.memory.metaspace.usage.ratio` | none | JVM Metaspace used divided by maximum/committed |
| `app.memory.process.rss.bytes` | none | Resident memory of the Java process |
| `app.memory.container.usage.bytes` | none | Total application cgroup memory, including Chromium |
| `app.memory.container.limit.bytes` | none | Finite cgroup hard limit |
| `app.memory.container.usage.ratio` | none | Total cgroup usage divided by hard limit |
| `app.memory.heap.max.to.container.ratio` | none | Maximum JVM heap divided by cgroup limit |
| `app.memory.container.limit.configured` | none | `1` finite limit, `0` absent/unlimited |
| `app.cache.size` | `cache` | Estimated entries currently held by a local Caffeine cache |
| `app.cache.maximum.entries` | `cache` | Configured hard entry limit |
| `app.cache.expiration.seconds` | `cache` | Configured expire-after-write duration |
| `app.cache.requests` | `cache`, `result=hit\|miss` | Cumulative cache lookup results |
| `app.cache.evictions` | `cache` | Cumulative size-based or expiration evictions |
| `app.pdf.concurrent.active` | none | PDF generations currently holding Chromium execution slots |
| `app.pdf.concurrent.limit` | none | Configured concurrent PDF limit per application instance |
| `app.pdf.queue.size` | none | PDF requests currently waiting for an execution slot |
| `app.pdf.queue.capacity` | none | Configured bounded PDF queue capacity per instance |
| `app.pdf.requests` | `result=success\|error\|rejected\|interrupted` | PDF generation outcomes |
| `app.pdf.slot.held` | none | Time spent holding a PDF execution slot |
| `app.pdf.memory.increase` | `scope=java_process\|container` | Positive RSS/cgroup increase observed while a PDF slot is held |

Never add session IDs, user IDs, IPs, endpoints, tokens, cache keys or credentials as labels. Full
production thresholds, CloudWatch alarms, failure drills and rollback steps are in
`docs/runbooks/multi-az-valkey-operations.md`.

Prometheus loads `observability/prometheus/rules/memory-envelope-alerts.yml` and
`observability/prometheus/rules/caffeine-cache-alerts.yml`. The Caffeine rules warn when a cache
stays above 90% of its entry limit or sustains more than one eviction per second. Production must
also monitor EC2 host memory and OOM kills through CloudWatch Agent because JVM, cgroup and cache
metrics do not replace host-level telemetry.
