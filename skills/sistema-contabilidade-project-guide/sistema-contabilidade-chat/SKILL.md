---
name: sistema-contabilidade-chat
description: Use only to assess or restore legacy chat references in sistema_contabilidade. The current worktree has no chat source, endpoints, page or configuration.
---

# Sistema Contabilidade Chat

The current worktree has no `chat` package, `/mensagens` page, chat REST API, WebSocket endpoint, RabbitMQ chat configuration or chat tests.

## Workflow

1. Read `references/chat.md`.
2. Confirm the required source exists before editing UI, config, security or observability.
3. Treat old chat documentation as historical only; do not recreate contracts from it without an approved specification.
4. If restoring the feature, add controller, service, repository, tests, page assets and configuration together.
5. Route only an approved restoration task here; normal UI, deploy and monitoring work must not assume chat exists.

## Validation

- Verify the restored routes and source with `rg --files`.
- Run focused controller/service tests and the relevant security/UI checks.
