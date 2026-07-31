# Multi-AZ RDS and Valkey operations

## Purpose and invariants

This runbook operates two EC2 application instances against an Amazon RDS MySQL Multi-AZ DB
Cluster and an ElastiCache Valkey replication group.

- RDS: one writer and two readers across three Availability Zones.
- Valkey: cluster mode disabled, one primary and one replica in different Availability Zones.
- The application uses RDS cluster endpoints, never individual instance endpoints.
- The application uses only the Valkey primary endpoint. The replica exists for automatic failover.
- Authentication, writes, DDL, background work and safe fallbacks use the writer.
- Authenticated `GET`/`HEAD` requests can use readers only inside read-only transactions.
- Reader or Valkey failure must degrade to the writer/local behavior, not remove an EC2 from ALB.
- Secrets belong in AWS Secrets Manager and process environment, never source, logs or command
  history.

## Profile behavior

| Profile | Database routing | Valkey features | Schema |
|---|---|---|---|
| `local` | Disabled; `spring.datasource.*` | Redis local; distributed features disabled | Hibernate `update`; Flyway disabled |
| tests | Disabled unless a focused routing test enables it | In-memory/fakes by default | H2; Flyway disabled by default |
| `prod` | Enabled; writer and reader cluster endpoints | Sticky, global rate limit and report cache enabled | Flyway enabled; Hibernate `validate` |

`SPRING_DATASOURCE_URL` is a temporary compatibility fallback for the writer only. New production
configuration must define `SPRING_DATASOURCE_WRITER_URL`. Remove the legacy variable after every
EC2 runs the explicit writer setting.

## Production environment

Store secret values in Secrets Manager. Inject them into both EC2 processes without printing them.

### Database data protection

RDS storage encryption with a customer-managed KMS key is mandatory. It protects the complete
database storage, automated backups, snapshots and replicas, including structural and analytical
columns that must remain queryable by MySQL. An existing unencrypted RDS database must be migrated
through an encrypted snapshot copy and restore before this release; storage encryption cannot be
substituted by application field converters.

The application additionally encrypts sensitive domain strings with AES-256-GCM and uses
context-separated HMAC-SHA-256 blind indexes for equality searches. Configure both EC2 instances
with the same stable secret:

```text
DB_COLUMN_CRYPTO_SECRET=<at least 32 random bytes from Secrets Manager>
APP_DB_CRYPTO_BACKFILL_ENABLED=true
```

`DB_COLUMN_CRYPTO_SECRET` must be independent from database credentials. The
`SESSION_CRYPTO_SECRET` fallback exists for compatibility, but production must set the dedicated
secret explicitly. Do not rotate or remove it in place: existing ciphertext and blind indexes
would become unreadable. Rotation requires a versioned dual-key migration, complete re-encryption
and blind-index rebuild.

The backfill is idempotent and encrypts legacy plaintext after Flyway V7. Keep maintenance mode
enabled while it runs. After verifying that no protected column contains legacy plaintext, set
`APP_DB_CRYPTO_BACKFILL_ENABLED=false` on both instances to avoid full-table scans on later starts.
New and updated entities remain encrypted by JPA converters.

Soft delete uses `deleted_at`; normal Hibernate repository reads omit deleted rows. Physical purge,
retention and restore are separate administrative workflows and must not bypass audit requirements.

### RDS endpoints and routing

```text
SPRING_DATASOURCE_WRITER_URL=jdbc:mysql://<cluster-writer>:3306/<database>?sslMode=VERIFY_IDENTITY&serverTimezone=America/Sao_Paulo
SPRING_DATASOURCE_READER_URL=jdbc:mysql://<cluster-reader>:3306/<database>?sslMode=VERIFY_IDENTITY&serverTimezone=America/Sao_Paulo
DB_USERNAME=<from Secrets Manager>
DB_PASSWORD=<from Secrets Manager>
APP_DB_ROUTING_ENABLED=true
APP_DB_STICKY_SECONDS=10
```

Do not place credentials in JDBC URLs. Use the current AWS/RDS CA trust chain and
`sslMode=VERIFY_IDENTITY`; do not use `trustAll`, `VERIFY_CA` without hostname validation or
individual instance endpoints.

When the JDBC URL references `file:/app/rds-truststore.p12`, keep the RDS truststore on the EC2
host and mount it read-only into every application container. Do not bake environment-specific
truststores into the image.

```bash
docker run \
  --mount type=bind,src=/home/ec2-user/rds-truststore.p12,dst=/app/rds-truststore.p12,readonly \
  ...
```

Before registering the target in the ALB, use `docker inspect` to confirm that this mount has
`RW=false`.

Approved Hikari defaults per EC2:

| Setting | Writer | Reader | Environment override |
|---|---:|---:|---|
| maximum pool size | 12 | 24 | `APP_DB_WRITER_MAX_POOL_SIZE`, `APP_DB_READER_MAX_POOL_SIZE` |
| minimum idle | 2 | 4 | `APP_DB_WRITER_MIN_IDLE`, `APP_DB_READER_MIN_IDLE` |
| connection timeout | 3 s | 3 s | `APP_DB_CONNECTION_TIMEOUT_MS` |
| validation timeout | 1 s | 1 s | `APP_DB_VALIDATION_TIMEOUT_MS` |
| idle timeout | 5 min | 5 min | `APP_DB_IDLE_TIMEOUT_MS` |
| max lifetime | 10 min | 10 min | `APP_DB_MAX_LIFETIME_MS` |
| keepalive | 2 min | 2 min | `APP_DB_KEEPALIVE_TIME_MS` |

Across two EC2 instances, defaults permit at most 24 writer and 48 reader pool connections.
Changes require checking the RDS connection limit and keeping alert headroom.

### Valkey endpoint, TLS and limits

```text
SPRING_DATA_REDIS_HOST=<primary endpoint without scheme or port>
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_USERNAME=<RBAC user from Secrets Manager>
SPRING_DATA_REDIS_PASSWORD=<from Secrets Manager>
SPRING_DATA_REDIS_SSL_ENABLED=true
APP_RATE_LIMIT_VALKEY_ENABLED=true
APP_RATE_LIMIT_MAX_REQUESTS=120
APP_RATE_LIMIT_WINDOW_SECONDS=60
APP_RELATORIO_RESUMO_CACHE_ENABLED=true
APP_RELATORIO_RESUMO_CACHE_TTL_SECONDS=30
APP_RELATORIO_RESUMO_CACHE_MAX_BYTES=131072
```

Approved Lettuce defaults per EC2: connect timeout `1s`, command timeout `1s`, maximum active `32`,
maximum idle `8`, minimum idle `2`, maximum wait `100ms`. Overrides are
`APP_VALKEY_CONNECT_TIMEOUT`, `APP_VALKEY_COMMAND_TIMEOUT`, `APP_VALKEY_POOL_MAX_ACTIVE`,
`APP_VALKEY_POOL_MAX_IDLE`, `APP_VALKEY_POOL_MIN_IDLE` and `APP_VALKEY_POOL_MAX_WAIT`.

Key TTLs and limits:

- sticky writer: 10 seconds, key `sc:db-sticky:v1:<session UUID>`;
- global rate limit: 120 requests per 60 seconds, hashed client bucket;
- financial summary cache: 30 seconds, maximum 128 KiB per entry;
- reader acquisition circuit: retry after 5 seconds.

Never log Valkey keys. They can identify sessions or request buckets even when values are hashed.

### Forwarded headers and JVM DNS

Production sets `server.forward-headers-strategy=native`. The ALB/Tomcat layer determines
`request.getRemoteAddr()`; application code must not parse client-supplied `X-Forwarded-For`.

Set on both EC2 services:

```text
JAVA_TOOL_OPTIONS=-Dnetworkaddress.cache.ttl=60 -Dnetworkaddress.cache.negative.ttl=10
```

These TTLs let new RDS and Valkey endpoint addresses be resolved after failover. Restart each
service after changing environment variables; a process restart is required to reload them.

### EC2, container and JVM memory envelope

Production enables `APP_MEMORY_ENVELOPE_ENFORCED=true`. Startup fails when the process cannot find
a finite cgroup memory limit or when the maximum JVM heap exceeds the approved percentage of that
limit. This is intentional: heap-only monitoring does not include direct buffers, thread stacks,
native libraries or Chromium child processes.

Before choosing byte values, record the EC2 RAM and subtract:

- the greater of 1 GiB or 20% for the operating system;
- CloudWatch/SSM/security agents and reverse proxy memory;
- any other container or process running on the instance.

The remainder is the maximum application cgroup budget. Initially keep JVM maximum heap at or below
50% of that budget:

```text
APP_MEMORY_ENVELOPE_ENFORCED=true
APP_MEMORY_MAX_HEAP_TO_CONTAINER_RATIO=0.50
APP_MEMORY_MONITOR_HEAP_ALERT_THRESHOLD=0.70
APP_MEMORY_MONITOR_CONTAINER_ALERT_THRESHOLD=0.70
APP_MEMORY_MONITOR_CONTAINER_CRITICAL_THRESHOLD=0.80
APP_MEMORY_MONITOR_SCHEDULED_LOGGING_ENABLED=true
```

The image supplies this safe initial JVM policy:

```text
JDK_JAVA_OPTIONS=-XX:MaxRAMPercentage=50.0 -XX:InitialRAMPercentage=25.0 -XX:+ExitOnOutOfMemoryError
```

If deployment overrides `JDK_JAVA_OPTIONS`, it must preserve an equivalent `-Xmx` or
`-XX:MaxRAMPercentage` and `-XX:+ExitOnOutOfMemoryError`.

Docker example, using values approved from the EC2 inventory and load test:

```bash
docker run \
  --memory="<approved hard limit>" \
  --memory-reservation="<approved soft limit below hard limit>" \
  --env-file /etc/sistema-contabilidade/env \
  ...
```

For a direct `systemd` service, configure both controls in the unit:

```ini
[Service]
MemoryHigh=<approved soft limit>
MemoryMax=<approved hard limit>
EnvironmentFile=/etc/sistema-contabilidade/env
ExecStart=/usr/bin/java -jar /opt/sistema-contabilidade/app.jar
```

After changing `--env-file` values, recreate the container; a restart does not reload the file.
Validate the effective envelope before registering the target:

```bash
docker inspect --format '{{.HostConfig.Memory}}' <container>
docker stats --no-stream <container>
curl --fail --silent http://127.0.0.1:8080/actuator/prometheus \
  | grep '^app_memory_'
```

Required signals:

- `app_memory_container_limit_configured` is `1`;
- `app_memory_heap_max_to_container_ratio` is at most `0.50`;
- steady-state `app_memory_container_usage_ratio` is below `0.70`;
- load-test peak remains below `0.80`;
- `app_memory_process_rss_bytes` tracks Java RSS;
- `app_memory_container_usage_bytes` tracks Java plus Chromium and other child processes.

Keep the EC2/CloudWatch Agent alarm for host memory below 75%. Application metrics cannot replace
host-level memory and OOM-kill monitoring.

### Local Caffeine cache budget

All local Caffeine caches are created by one configuration and always use `maximumSize`,
`expireAfterWrite` and statistics recording. Defaults:

| Cache | Maximum entries | Expire after write | Purpose |
|---|---:|---:|---|
| `userDetails` | 500 | 5 minutes | Authentication details indexed by email, ID or Cognito subject |
| `itemDescricoes` | 8 | 5 minutes | Expense/revenue description catalogs |
| `itemTiposDocumento` | 8 | 5 minutes | Document-type catalogs |
| `stickyWriterLocal` | 100,000 | `APP_DB_STICKY_SECONDS`, default 10 seconds | Local fallback when Valkey sticky state is disabled |

Runtime overrides:

```text
APP_CACHE_CAFFEINE_USER_DETAILS_MAXIMUM_SIZE=500
APP_CACHE_CAFFEINE_USER_DETAILS_EXPIRE_AFTER_WRITE=5m
APP_CACHE_CAFFEINE_ITEM_DESCRICOES_MAXIMUM_SIZE=8
APP_CACHE_CAFFEINE_ITEM_DESCRICOES_EXPIRE_AFTER_WRITE=5m
APP_CACHE_CAFFEINE_ITEM_TIPOS_DOCUMENTO_MAXIMUM_SIZE=8
APP_CACHE_CAFFEINE_ITEM_TIPOS_DOCUMENTO_EXPIRE_AFTER_WRITE=5m
APP_CACHE_CAFFEINE_STICKY_WRITER_MAXIMUM_SIZE=100000
```

`maximumSize` limits entries, not bytes. Do not raise a limit only to suppress evictions: confirm
heap headroom, hit/miss rates and entry payload size first. Never put PDFs, Base64, tokens, complete
CPF/CNPJ, S3 paths or cache keys in cache metric labels.

Verify after deployment:

```bash
curl --fail --silent http://127.0.0.1:8080/actuator/prometheus \
  | grep '^app_cache_'
```

Required signals:

- all four cache names expose size, maximum entries and expiration;
- `app_cache_size / app_cache_maximum_entries` remains below `0.90`;
- hit/miss counters move after normal traffic;
- sustained eviction pressure is investigated together with JVM heap and cgroup usage.

## Create the Valkey replication group

1. In ElastiCache, create a Valkey replication group with cluster mode disabled.
2. Select `cache.t4g.small`, one shard, one primary and one replica.
3. Place primary and replica in different Availability Zones using the private subnet group.
4. Enable Multi-AZ and automatic failover.
5. Enable encryption in transit and at rest. Do not enable a plaintext endpoint.
6. Create a least-privilege RBAC user/user group. Store credentials in Secrets Manager.
7. Attach a Security Group allowing inbound TCP `6379` only from the EC2 application Security
   Group. Do not allow CIDRs such as `0.0.0.0/0`.
8. Record the primary endpoint for `SPRING_DATA_REDIS_HOST`; do not configure the reader endpoint.
9. From each EC2, verify DNS and TLS connectivity without putting credentials in shell history.

## ALB health

Configure the target group health check:

```text
Path: /actuator/health/alb
Success code: 200
```

The endpoint is public and returns only `ping` plus `writer`. It intentionally excludes reader,
Valkey, disk-space and other contributors. A writer failure makes the target unhealthy. Reader or
Valkey failures remain visible through metrics and alarms while application fallbacks preserve
availability.

Smoke check from each EC2:

```bash
curl --fail --silent --show-error http://127.0.0.1:8080/actuator/health/alb
```

Expected status is `UP`, with components `ping` and `writer` only.

## Metrics and cardinality

Scrape `/actuator/prometheus`. Micrometer metric names and allowed tags:

| Metric | Allowed tags |
|---|---|
| `app.db.route.total` | `route=writer|reader`, fixed `reason` values |
| `app.db.reader.connection.failures` | none |
| `app.db.reader.circuit.state` | none; `0` closed, `0.5` probe, `1` open |
| `app.db.sticky.total` | fixed `result` values |
| `app.rate_limit.total` | `backend=valkey|local`, fixed `result` values |
| `app.valkey.operation.errors` | fixed `operation` values |
| `app.relatorio.resumo.cache.total` | `result=hit|miss|bypass|error` |
| `app.memory.heap.usage.ratio` | none |
| `app.memory.metaspace.usage.ratio` | none |
| `app.memory.process.rss.bytes` | none |
| `app.memory.container.usage.bytes` | none |
| `app.memory.container.limit.bytes` | none |
| `app.memory.container.usage.ratio` | none |
| `app.memory.heap.max.to.container.ratio` | none |
| `app.memory.container.limit.configured` | none; `1` finite, `0` absent |

Never add IDs, IPs, URIs, tokens, credentials, session values, roles or cache keys to these labels.
Logs must also omit credentials and session/cache keys.

## Alarms

Create CloudWatch alarms and route them to the production notification channel:

| Service | Metric | Warning | Critical/action |
|---|---|---:|---:|
| RDS readers | ReplicaLag | `> 5s` | `> 10s`; force writer if sustained |
| RDS | CPUUtilization | `> 70%` | investigate pool/query load |
| RDS | FreeableMemory | `< 2 GiB` | scale or reduce load |
| RDS | DatabaseConnections | `> 70%` of limit | reduce pools/load before exhaustion |
| Valkey | EngineCPUUtilization | `> 65%` | scale/investigate Lua and traffic |
| Valkey | Evictions | `> 0` | critical; restore memory headroom |
| Valkey | DatabaseMemoryUsagePercentage | `> 80%` | scale or reduce cache pressure |
| Valkey replica | ReplicationLag | `> 1s` | investigate failover readiness |
| EC2 | `mem_used_percent` via CloudWatch Agent | `> 70%` | `> 75%`; drain/scale |
| Application cgroup | `app.memory.container.usage.ratio` | `>= 70%` for 5 min | `>= 80%`; drain/reduce load |
| Application JVM | `app.memory.heap.usage.ratio` | `>= 70%` for 5 min | inspect GC/heap dump procedure |
| Application envelope | `app.memory.container.limit.configured` | n/a | `< 1`; do not register in ALB |

Also alert when application counters show reader acquisition failures, an open reader circuit,
Valkey operation errors or sustained `backend=local` rate limiting. Metric labels must remain the
fixed sets above.

## Deployment across two EC2 instances

1. Confirm Valkey TLS connectivity from both EC2 instances.
2. Confirm writer and reader cluster endpoints and primary keys on all routed tables.
3. Create a manual RDS snapshot.
4. Enable maintenance mode and drain both EC2 targets from the ALB.
5. Deploy to the first EC2 with explicit writer/reader, Valkey, database-crypto and memory-envelope
   environment plus a finite Docker/systemd memory limit.
6. For initial Flyway adoption only, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and follow
   `docs/runbooks/flyway-initial-adoption.md`.
7. Start the first EC2, validate Flyway history/schema, memory-envelope metrics and encryption
   backfill, then check ALB health and metrics.
8. Exercise one authenticated write, immediate sticky read, post-TTL reader read, rate limit and
   report-cache hit/miss.
9. Register the first EC2 in the ALB, then deploy and validate the second EC2 identically.
10. Disable `APP_DB_CRYPTO_BACKFILL_ENABLED` on both EC2 instances and restart them one at a time.
11. Register the second EC2. Disable maintenance mode.
12. Remove `SPRING_FLYWAY_BASELINE_ON_MIGRATE` or set it to `false` permanently.
13. Monitor alarms, fallback counters, connection pools and error logs for 24–48 hours.

## Failure drills

### Reader unavailable

- Expected: first failed acquisition increments `app.db.reader.connection.failures`, opens the
  five-second circuit and falls back to writer.
- Confirm `/actuator/health/alb` stays `UP` while writer is available.
- If failure or lag persists, set `APP_DB_ROUTING_ENABLED=false` on both EC2 instances and restart
  them one at a time.

### Valkey unavailable

- Expected: sticky lookup forces writer, rate limiting falls back per EC2, and report summaries
  load from the database.
- Confirm Valkey error and local fallback metrics increase, while ALB health stays `UP`.
- To reduce repeated operations during a long outage, set
  `APP_RATE_LIMIT_VALKEY_ENABLED=false` and `APP_RELATORIO_RESUMO_CACHE_ENABLED=false`, then restart
  both services. Database routing can stay enabled; sticky read failures already fail safe.

### Writer unavailable

- Expected: `/actuator/health/alb` becomes `DOWN`; ALB removes the affected EC2 target.
- Investigate RDS cluster failover and DNS resolution. Do not point the writer URL at a reader.
- Confirm the cluster writer endpoint resolves to the promoted writer before restoring targets.

## Rollback

1. Drain one EC2 target at a time.
2. Before V7 backfill, restore the previous application artifact and its compatible environment
   configuration. After backfill starts, the previous artifact is incompatible with ciphertext;
   restore the pre-deploy RDS snapshot instead.
3. If the old artifact supports only one datasource, point `SPRING_DATASOURCE_URL` at the RDS
   writer cluster endpoint. Never point it at the reader endpoint.
4. Restart, validate local health and application write/read behavior, then register the target.
5. Repeat for the second EC2.
6. Schema rollback requires the pre-deploy snapshot or a corrective forward migration. Never edit
   or delete `flyway_schema_history` manually.
7. Keep Valkey data disposable. Do not restore sticky, rate-limit or report-cache keys from backup.

After rollback, preserve logs and metric timelines without copying secrets, session values or
cache keys into tickets.
