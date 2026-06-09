# Architecture

## Overview

```
┌─────────────┐     ┌──────────────────┐     ┌─────────────┐
│  HTTP/WS    │────▶│  Application     │────▶│  Domain     │
│  (Javalin)  │     │  Services        │     │  Models     │
└─────────────┘     └────────┬─────────┘     └─────────────┘
                             │
              ┌──────────────┼──────────────┐
              ▼              ▼              ▼
         PostgreSQL       Redis          WebSocket Hub
         (write/read)    (cache/RL)     (real-time)
                             │
                      Event Bus ──▶ Notifications (circuit breaker)
                                 ──▶ Activity Log
                                 ──▶ Cache invalidation
```

## Key decisions

**CQRS-lite**: `IssueWriteRepository` handles mutations; `BoardReadRepository` serves the board view. Reads are cached in Redis (30s TTL).

**Optimistic locking**: `issues.version` incremented on every update. Conflicting writes return 409 with current state.

**Sprint locks**: PostgreSQL advisory transaction locks prevent concurrent start/complete on the same sprint.

**Workflow engine**: Transitions stored per project. Validation runs before status change. Actions (e.g. auto-assign reviewer) applied in the same transaction.

**Event-driven side effects**: Mutations publish domain events. Handlers are synchronous in-process but decoupled from the write path — notification failures are queued and retried when the circuit breaker closes.

**RBAC**: Row-level access via `project_members`. Viewer role is read-only.

## Scaling notes

| Component | Stateful? | Scale strategy |
|-----------|-----------|----------------|
| HTTP API | No | Horizontal, behind load balancer |
| WebSocket | Yes (session map) | Sticky sessions or Redis pub/sub bridge |
| PostgreSQL | Yes | Read replicas for board/search queries |
| Redis | Yes | Cluster mode for cache + rate limits |

## Database

See `V1__schema.sql` for the full schema. Core entities: projects, issues (with parent-child), sprints, workflow statuses/transitions, comments, activity log, custom fields.

Full-text search uses PostgreSQL `tsvector` indexes on issues and comments.
