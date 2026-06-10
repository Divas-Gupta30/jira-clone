# Project Board — Backend

Jira-like project management backend in Java 21 with Javalin (no Spring Boot).

## Prerequisites

- JDK 21
- Maven
- Docker (for Postgres + Redis)

## Quick start

```bash
docker-compose up -d postgres redis
mvn package -DskipTests
java -jar target/project-board-1.0.0.jar
```

Server starts on **http://localhost:8001** (override with `PORT=8001`).

Or run everything in Docker:

```bash
docker-compose up --build
```

Run via Maven (useful from IDE):

```bash
mvn compile exec:java
```

## Auth

Every `/api/*` request needs the acting user:

```
X-User-Id: user_lead
```

Seed users for project `proj_abc`: `user_admin`, `user_lead`, `user_member`, `user_viewer`.

## API

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/projects/:id/issues` | Create issue |
| GET | `/api/v1/projects/:id/board` | Board view (cached in Redis) |
| PATCH | `/api/v1/issues/:id` | Update issue (optimistic lock via `version`) |
| POST | `/api/v1/issues/:id/transitions` | Workflow transition |
| GET | `/api/v1/projects/:id/sprints` | List sprints |
| POST | `/api/v1/sprints/:id/start` | Start sprint (advisory lock) |
| POST | `/api/v1/sprints/:id/complete` | Complete sprint with carry-over |
| GET/POST | `/api/v1/issues/:id/comments` | Comments |
| GET | `/api/v1/projects/:id/activity` | Activity feed |
| GET | `/api/v1/search?q=...&filter=...` | Full-text + structured search |
| GET | `/api/health/live` | Liveness |
| GET | `/api/health/ready` | Readiness |
| GET | `/api/metrics` | Prometheus metrics |

**API docs:** [Swagger UI](http://localhost:8001/api/docs) · [OpenAPI spec](http://localhost:8001/api/docs/openapi.yaml)

Unversioned `/api/...` routes also work for backward compatibility.

## Examples

Health check:

```bash
curl http://localhost:8001/api/health/live
```

Board:

```bash
curl http://localhost:8001/api/v1/projects/proj_abc/board -H "X-User-Id: user_member"
```

Transition issue (use the current `version` from GET/PATCH response):

```bash
curl -X POST http://localhost:8001/api/v1/issues/PROJ-123/transitions \
  -H "X-User-Id: user_member" \
  -H "Content-Type: application/json" \
  -d '{"toStatus":"In Review","version":3}'
```

When copy-pasting multi-line curls, use `\` at the end of each line — not `\n`.

- Invalid transitions → `422` with `allowed_transitions`
- Stale version → `409` with current issue state
- Missing `X-User-Id` → `401`

## WebSocket

```
ws://localhost:8001/ws/board?project_id=proj_abc&user_id=user_lead
```

Events: `issue_created`, `issue_updated`, `issue_moved`, `comment_added`, `sprint_updated`, `presence`.

## Deploy on Render

1. Push this repo to GitHub.
2. In [Render Dashboard](https://dashboard.render.com/) → **New** → **Blueprint** → connect the repo.
3. Render provisions **project-board** (web), **projectboard-db** (Postgres), and **projectboard-redis** (Key Value) from [`render.yaml`](render.yaml).
4. After deploy, open `https://<your-service>.onrender.com/api/health/live`.

The app reads `DATABASE_URL` and `REDIS_URL` from linked services, binds to Render's `PORT`, and uses `RENDER_EXTERNAL_URL` for OpenAPI/Swagger links. `ADMIN_API_KEY` is auto-generated — copy it from the service's **Environment** tab for `/api/v1/admin/*` calls.

Free-tier web services spin down after inactivity; the first request after idle may take ~30s.

## Demo hosting

Host on your **internal network** (company VPN / internal VM):

```bash
cp .env.demo.example .env          # set PUBLIC_BASE_URL to http://YOUR_INTERNAL_IP:8001
docker compose -f docker-compose.demo.yml up -d --build
```

Full guide: **[docs/internal-hosting.md](docs/internal-hosting.md)** (includes admin seed APIs)

Share with reviewers:

- Swagger: `http://<internal-ip>:8001/api/docs`
- Header: `X-User-Id: user_member` (or `user_lead`, `user_admin`, `user_viewer`)

## IntelliJ

1. Open the project root (where `pom.xml` is)
2. When prompted, **Import as Maven project** (or right-click `pom.xml` → **Maven** → **Reload project**)
3. Set Project SDK to JDK 21
4. Run **Main (Maven)** from run configurations, or `mvn compile exec:java`

If you see *"Maven project configuration required for module isn't available"* (often when running **tests**):

- **Build → Rebuild Project** after reopening the project
- Delete stale run configs that reference module `jira-clone` (use `project-board` instead)
- Run tests via **Integration Tests (Maven)** run config, or **ApiIntegrationTest** (JUnit, with `-Dintegration.local=true`)
- Or from terminal: `mvn test -Dintegration.local=true`

The IDE module is `project-board` (matches the Maven `artifactId` in `pom.xml`).

If you see "Unresolved compilation problems" at runtime, don't use the default Application runner with a broken IDE build — use **Main (Maven)** instead.

## Architecture

Hexagonal layout:

- `domain/` — models, events, ports
- `application/` — use cases
- `infrastructure/` — JDBC, Redis, WebSocket, notifications
- `api/` — HTTP handlers, middleware

Domain events drive activity log, notifications (circuit breaker), WebSocket broadcasts, and cache invalidation. Board reads use a separate query path (CQRS-lite) with Redis caching.

See `docs/architecture.md` and `docs/adrs/` for details.

## Integration tests

API integration tests spin up the full app against PostgreSQL and Redis.

If `docker compose` Postgres (5432) and Redis (6379) are already running locally, tests **auto-detect** them — no flags needed:

```bash
docker compose up -d postgres redis
mvn test
```

Force Testcontainers (isolated containers, requires Docker):

```bash
mvn test -Dintegration.testcontainers=true
```

Force local services explicitly:

```bash
mvn test -Dintegration.local=true
```

Tests cover health, metrics, OpenAPI docs, auth/RBAC, issues (CRUD, transitions, optimistic locking), board, sprints, comments, activity, search, and unversioned API aliases.

In IntelliJ, use run config **Integration Tests (Maven)** or **ApiIntegrationTest** (requires `docker compose up -d postgres redis` for local mode).

## Load test

```bash
k6 run load-test/board-viewers.js
```

Targets 100 concurrent board viewers against `/api/v1/projects/proj_abc/board`.

## Project structure

```
src/main/java/com/projectboard/
├── domain/
├── application/
├── infrastructure/
├── api/
└── Main.java
```
