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

## Redis

- Root `docker-compose.yml` starts Redis at `127.0.0.1:6379`.
- Redis uses `redis-data`, AOF and `redis-cli ping` healthcheck.
- Active Spring cache remains Caffeine unless explicitly changed.

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
- Do not expose values of tokens, passwords or secrets in logs/chat.
