# Chat Reference

## Current State

- No chat implementation is present in this worktree.
- Do not assume `/mensagens`, `/api/v1/chat`, `/ws/chat`, SockJS/STOMP, RabbitMQ outbox, unread badges or chat metrics exist.
- Historical names and environment variables are intentionally omitted because they cannot be validated against the current source.

## Restoration Boundary

An approved restoration must define its API, authorization, persistence, delivery mode, failure handling, observability and tests before code is added.
