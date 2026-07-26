# Deploy and Config Reference

## Spring Profiles and Properties

- `application.properties` imports `.env`.
- Default profile: `local`.
- `application.properties` defines `app.auth.provider=${APP_AUTH_PROVIDER:local}`.
- `application-prod.properties` defines `app.auth.provider=${APP_AUTH_PROVIDER:cognito}`.
- `app.security.cors.allowed-origins` comes from `APP_CORS_ALLOWED_ORIGINS`, with local fallback to `http://localhost:3000`.

## Local Profile

`application-local.properties` defines:

- local MySQL
- `spring.jpa.hibernate.ddl-auto=update`
- local storage in `uploads/itens`
- PDF limit: `20971520`
- local Redis
- `spring.thymeleaf.cache=false`

## Database migrations

- Production schema is managed by Flyway migrations under `src/main/resources/db/migration`.
- Production uses Hibernate `validate`; local and regular H2 tests keep Flyway disabled by default.
- Existing production adopts Flyway with baseline version 1 and
  `SPRING_FLYWAY_BASELINE_ON_MIGRATE=true` for the first startup only.
- Follow `docs/runbooks/flyway-initial-adoption.md` before executing the initial migration.

## RDS writer/reader routing

- Routing is controlled by `app.database.routing.enabled` / `APP_DB_ROUTING_ENABLED` and remains
  disabled for the local profile.
- Production accepts `SPRING_DATASOURCE_WRITER_URL` and `SPRING_DATASOURCE_READER_URL`; the legacy
  `SPRING_DATASOURCE_URL` remains a temporary fallback for the writer URL.
- Use native RDS cluster writer/reader endpoints, never individual instance endpoints.
- Requests without a validated `SC_SESSION`, authentication queries, write transactions, DDL and
  background work use the writer.
- Authenticated `GET`/`HEAD` requests can reach the reader only inside an explicitly read-only
  Spring transaction.
- Reader connection acquisition failures fall back to the writer and open a five-second circuit;
  SQL failures after acquisition are never retried.
- The primary application `DataSource` is a `LazyConnectionDataSourceProxy` over the routing data
  source so the transaction read-only flag exists before physical connection selection.

## Redis

- Root `docker-compose.yml` starts Redis at `127.0.0.1:6379`.
- Redis uses `redis-data`, AOF and `redis-cli ping` healthcheck.
- Active Spring cache remains Caffeine unless explicitly changed.
- Production Valkey uses `SPRING_DATA_REDIS_HOST`, `SPRING_DATA_REDIS_PORT`,
  `SPRING_DATA_REDIS_USERNAME`, `SPRING_DATA_REDIS_PASSWORD` and
  `SPRING_DATA_REDIS_SSL_ENABLED`; credentials must never enter source or logs.
- Sticky writer uses `sc:db-sticky:v1:<sessaoId>` with TTL configured by
  `APP_DB_STICKY_SECONDS` (default 10 seconds).
- Authenticated successful API mutations renew sticky after the response. While active,
  `GET`/`HEAD` requests from the same validated session use the writer.
- Sticky lookup failures fail safe to writer. Sticky renewal failures do not fail the mutation.

## Docker Env Gotcha

When deploying with:

```text
docker run --env-file .env
```

Changing `.env` and only restarting the container does not reapply environment variables. Recreate the container.

## CloudFront, ALB and DNS

- Project has run behind CloudFront with ALB as origin.
- `/assets/*` may require a separate behavior with `Origin request policy = Managed-AllViewer` for ALB-backed origins.
- Without that, assets can return 502 while HTML works.
- Root domain and `www` can share the same CloudFront distribution.
- Ideal CloudFront certificate in `us-east-1` covers:
  - `sacsdigital.com.br`
  - `*.sacsdigital.com.br`
- CloudFront certificate alone is not enough for `www` if origin TLS handshake lands on an ALB certificate that does not cover `www.sacsdigital.com.br`.
- Extra `NS` records in Route 53 are not the fix for this scenario.
- Focus on `A` aliases, CloudFront behavior, certificates and ALB listener/origin settings.

## Security

- `.env` can silently override storage, database, cache and secrets.
- Do not expose values of tokens, passwords or secrets in logs or task updates.
