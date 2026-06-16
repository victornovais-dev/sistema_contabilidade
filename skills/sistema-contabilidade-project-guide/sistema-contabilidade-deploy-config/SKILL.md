---
name: sistema-contabilidade-deploy-config
description: Use for sistema_contabilidade environment configuration, .env, Spring profiles, Docker, Redis, AWS S3, CloudFront, ALB, DNS, certificates, production CORS and deployment incidents.
---

# Sistema Contabilidade Deploy and Config

Use this skill when the task touches `.env`, Spring profiles, Docker, Redis, S3/local storage, CloudFront, ALB, DNS, certificates, production CORS, deployment or environment-specific bugs.

## First Read

1. Read `references/deploy-config.md`.
2. Inspect the specific config files involved.
3. If the task is auth production, also read `skills/sistema-contabilidade-auth-security/SKILL.md`.
4. If the task is static assets/CDN, also read `skills/sistema-contabilidade-ui-pages/SKILL.md`.

## Main Files

- `src/main/resources/application.properties`
- `src/main/resources/application-local.properties`
- `src/main/resources/application-prod.properties`
- `.env`
- `docker-compose.yml`
- `observability/docker-compose.yml` when monitoring stack is involved

## Workflow

1. Determine active profile and deployment mode.
2. Check if `.env` overrides the property under investigation.
3. Never print secrets; mask values.
4. For Docker `--env-file .env`, remember container restart does not reload changed env values; recreate the container.
5. For Cognito production boot failures, verify required env variables.
6. For CORS production failures, verify `APP_CORS_ALLOWED_ORIGINS`.
7. For CloudFront/ALB issues, distinguish viewer certificate from origin ALB certificate.
8. For static asset 502s behind CloudFront, inspect behavior/origin request policy.

## Validation

- Confirm environment variables without exposing values.
- Validate effective Spring properties.
- For CDN/DNS, test root and `www` separately.
- For Docker env changes, recreate container rather than only restarting.
