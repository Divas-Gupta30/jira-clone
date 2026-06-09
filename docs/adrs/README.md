# ADR-001: Javalin over Spring Boot

## Context
Assignment requires a backend prototype without mandating a framework. Need fast startup, minimal magic, clear layering.

## Decision
Use Javalin 6 on JDK 21 with manual dependency wiring in `Main.java`.

## Consequences
- No auto-configuration; wiring is explicit but readable
- Smaller artifact, faster cold start
- Team must implement middleware (auth, rate limit) manually — done in `api/middleware`

---

# ADR-002: In-process event bus

## Context
Need domain events for notifications, activity feed, WebSocket, cache invalidation.

## Decision
Synchronous in-process `EventPublisher` with separate handler concerns.

## Consequences
- Simple to reason about for a single-node prototype
- Production would move to message broker (Kafka/SQS) for the same event types
- Circuit breaker on notification path prevents cascading failures (Scenario 4)

---

# ADR-003: PostgreSQL advisory locks for sprint operations

## Context
Sprint start/complete must not run concurrently for the same sprint.

## Decision
Use `pg_advisory_xact_lock` keyed on sprint ID within the transaction.

## Consequences
- Lock released automatically on commit/rollback
- No extra locking table needed
- Locks are per-database; sharding by project would need a different strategy

---

# ADR-004: Optimistic concurrency for issues

## Context
Multiple users may edit the same issue simultaneously (Scenario 1).

## Decision
`version` column on issues; updates require expected version; 409 on mismatch.

## Consequences
- No long-held row locks during user think time
- Clients must retry with merged state
- Transitions also check version
