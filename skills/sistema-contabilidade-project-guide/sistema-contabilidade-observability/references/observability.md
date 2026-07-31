# Observability and Performance Reference

## Query Count

- `QueryCountStatementInspector` counts Hibernate queries per request.
- `QueryCountFilter`:
  - resets/ends context
  - adds `X-Query-Count`
  - publishes `http.server.query.count`
  - ignores `/actuator`, assets and `favicon`
- Query threshold is configurable by property.
- Query observability is local to this repo under `monitoring/query`.

## HTTP Timing

- `RequestTimingFilter`:
  - adds `X-App-Time-Ms`
  - adds `Server-Timing`
  - publishes Micrometer request duration metrics
  - logs slow requests above configured threshold
- `RequestMonitoringPathUtils` centralizes ignored paths.

## Memory

- `MemoryMonitoringMetrics` exposes heap and metaspace gauges.
- `MemoryMonitoringService` exposes heap, Metaspace, Java RSS and cgroup usage/limit gauges and logs
  snapshots/alerts when enabled.
- `LinuxMemoryRuntimeProbe` supports cgroup v2 and v1. Cgroup usage includes Chromium child
  processes; Java RSS does not.
- Production enables memory-envelope validation. Startup requires a finite Docker/systemd cgroup
  limit and rejects JVM maximum heap above the configured percentage of that limit.
- The container image defaults maximum JVM heap to 50% of cgroup memory. Runtime deployment must
  still configure Docker `--memory` or systemd `MemoryMax`.
- Prometheus rules alert when the cgroup limit is missing, container use reaches 70%/80%, heap use
  reaches 70%, or the heap budget exceeds 50% of the cgroup limit.
- Memory observability is under `monitoring/memory`.

## Cache

- Current app cache uses Caffeine.
- All Caffeine caches are built through `monitoring/cache/CaffeineCacheFactory`, which always applies
  an entry limit, expire-after-write and statistics recording.
- Declared local caches:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
  - `stickyWriterLocal`
- Per-cache settings are under `app.cache.caffeine.*`; the sticky-writer expiration remains tied to
  `app.database.sticky-writer.seconds`.
- Metrics expose size, configured maximum, expiration, hit/miss requests and evictions with only the
  fixed `cache` and `result` labels.
- Redis is configured and can run locally, but does not automatically speed up `/api/v1/itens`;
  application annotation caches remain local Caffeine caches on each EC2.
- Good cache candidates are stable auxiliary data such as roles, descriptions and document types.
- Caching `/api/v1/itens` requires careful invalidation because list data changes with verification, observation, upload and delete.

## Performance Notes

- `lista_comprovantes` hot path uses dedicated paginated projection in `ItemListPageRepositoryImpl`.
- That path now uses `Slice` instead of `Page` to avoid per-request `count(*)`.
- `relatorios` web summaries aggregate by category in the database through `RelatorioResumoCategoriaRow`.

## Database routing

- `app.db.route.total{route,reason}` counts writer/reader connection selections.
- `app.db.reader.connection.failures` counts reader acquisition failures that trigger writer
  fallback.
- `app.db.reader.circuit.state` exposes `0` for closed, `0.5` for half-open probe and `1` for open.
- `app.db.sticky.total{result}` counts sticky state checks and renewals. Results are `active`,
  `inactive`, `marked`, `read_error`, `write_error` and `disabled`.
- Routing metrics never include session IDs, user IDs, endpoints, credentials or other high-cardinality
  labels.
- `/actuator/health/alb` includes only `ping` and writer health. Reader/Valkey availability is
  observed separately through low-cardinality metrics documented in `observability/README.md` and
  `docs/runbooks/multi-az-valkey-operations.md`.
