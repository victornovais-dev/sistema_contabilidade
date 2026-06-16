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
- `MemoryMonitoringService` logs snapshots/alerts when enabled.
- Memory observability is under `monitoring/memory`.

## Cache

- Current app cache uses Caffeine.
- Declared caches:
  - `userDetails`
  - `itemDescricoes`
  - `itemTiposDocumento`
- Redis is configured and can run locally, but does not automatically speed up `/api/v1/itens` while cache type and app `cacheManager()` keep using Caffeine.
- Good cache candidates are stable auxiliary data such as roles, descriptions and document types.
- Caching `/api/v1/itens` requires careful invalidation because list data changes with verification, observation, upload and delete.

## Performance Notes

- `lista_comprovantes` hot path uses dedicated paginated projection in `ItemListPageRepositoryImpl`.
- That path now uses `Slice` instead of `Page` to avoid per-request `count(*)`.
- `relatorios` web summaries aggregate by category in the database through `RelatorioResumoCategoriaRow`.
