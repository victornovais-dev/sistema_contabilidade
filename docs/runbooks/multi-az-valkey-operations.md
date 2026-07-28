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

Also alert when application counters show reader acquisition failures, an open reader circuit,
Valkey operation errors or sustained `backend=local` rate limiting. Metric labels must remain the
fixed sets above.

## Deployment across two EC2 instances

1. Confirm Valkey TLS connectivity from both EC2 instances.
2. Confirm writer and reader cluster endpoints and primary keys on all routed tables.
3. Create a manual RDS snapshot.
4. Enable maintenance mode and drain both EC2 targets from the ALB.
5. Deploy to the first EC2 with explicit writer/reader and Valkey environment.
6. For initial Flyway adoption only, set `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` and follow
   `docs/runbooks/flyway-initial-adoption.md`.
7. Start the first EC2, validate Flyway history/schema, then check ALB health and metrics.
8. Exercise one authenticated write, immediate sticky read, post-TTL reader read, rate limit and
   report-cache hit/miss.
9. Register the first EC2 in the ALB, then deploy and validate the second EC2 identically.
10. Register the second EC2. Disable maintenance mode.
11. Remove `SPRING_FLYWAY_BASELINE_ON_MIGRATE` or set it to `false` permanently.
12. Monitor alarms, fallback counters, connection pools and error logs for 24–48 hours.

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
2. Restore the previous application artifact and its compatible environment configuration.
3. If the old artifact supports only one datasource, point `SPRING_DATASOURCE_URL` at the RDS
   writer cluster endpoint. Never point it at the reader endpoint.
4. Restart, validate local health and application write/read behavior, then register the target.
5. Repeat for the second EC2.
6. Schema rollback requires the pre-deploy snapshot or a corrective forward migration. Never edit
   or delete `flyway_schema_history` manually.
7. Keep Valkey data disposable. Do not restore sticky, rate-limit or report-cache keys from backup.

After rollback, preserve logs and metric timelines without copying secrets, session values or
cache keys into tickets.
