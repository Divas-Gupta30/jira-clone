# Admin seed API

Administrative endpoints for bootstrapping demo data: users, projects, memberships, and workflow defaults. Intended for internal demos and test environments — not for production user management.

**Base path:** `/api/v1/admin/seed`

**Interactive docs:** [Swagger UI](/api/docs) (admin routes are documented in this guide; OpenAPI spec includes the Admin tag)

---

## Authentication

Every admin route is protected by `Middleware.adminAuth`. Access is granted if **either**:

| Method | Header | Notes |
|--------|--------|-------|
| API key | `X-Admin-Key: <ADMIN_API_KEY>` | Value from `ADMIN_API_KEY` env var (auto-generated on Render) |
| Platform admin user | `X-User-Id: user_admin` | User must have `ADMIN` role on at least one project |

If `ADMIN_API_KEY` is **not** configured (empty env var), only `X-User-Id` with a platform admin works.

`X-User-Id` is optional when using a valid `X-Admin-Key`; the request is attributed to `system`.

**Errors**

| Status | `error` | Cause |
|--------|---------|-------|
| 401 | `UNAUTHORIZED` | Missing both valid `X-Admin-Key` and `X-User-Id` |
| 403 | `FORBIDDEN` | `X-User-Id` present but user is not a platform admin |

Regular `/api/v1/*` routes still require `X-User-Id` separately — admin auth does not replace user auth on non-admin paths.

---

## Endpoints

### GET `/api/v1/admin/seed`

Returns a snapshot of all seed-managed users, projects, and project memberships.

**Response `200`**

```json
{
  "users": [
    { "id": "user_admin", "email": "admin@example.com", "displayName": "Admin User" }
  ],
  "projects": [
    { "id": "proj_abc", "key": "PROJ", "name": "Platform Team" }
  ],
  "members": [
    { "projectId": "proj_abc", "userId": "user_member", "role": "MEMBER" }
  ]
}
```

---

### POST `/api/v1/admin/seed/users`

Add a user.

**Body**

| Field | Type | Required |
|-------|------|----------|
| `id` | string | yes |
| `email` | string | yes |
| `displayName` | string | yes |

**Response `201`** — echoes the request body.

---

### DELETE `/api/v1/admin/seed/users/{userId}`

Remove a user and their project memberships.

**Response `204`**

---

### POST `/api/v1/admin/seed/projects`

Create a project with the default Kanban workflow (To Do → In Progress → In Review → Done) and transitions.

**Body**

| Field | Type | Required | Description |
|-------|------|----------|-------------|
| `id` | string | yes | Project ID, e.g. `proj_demo` |
| `key` | string | yes | Issue key prefix, e.g. `DEMO` → issues `DEMO-1`, `DEMO-2` |
| `name` | string | yes | Display name |
| `adminUserId` | string | no | User added as `ADMIN` member |

**Response `201`**

```json
{
  "id": "proj_demo",
  "key": "DEMO",
  "name": "Demo Team",
  "workflowStatuses": ["To Do", "In Progress", "In Review", "Done"]
}
```

---

### DELETE `/api/v1/admin/seed/projects/{projectId}`

Delete a project and all related data (issues, sprints, workflow, members). Invalidates the board cache for that project.

**Response `204`**

---

### POST `/api/v1/admin/seed/projects/{projectId}/members`

Add a project member.

**Body**

| Field | Type | Required | Values |
|-------|------|----------|--------|
| `userId` | string | yes | Existing user ID |
| `role` | string | yes | `ADMIN`, `PROJECT_LEAD`, `MEMBER`, `VIEWER` |

**Response `201`** — echoes the request body.

**Role permissions (summary)**

| Role | Read board | Create/edit issues | Start/complete sprints |
|------|------------|--------------------|------------------------|
| `VIEWER` | yes | no | no |
| `MEMBER` | yes | yes | no |
| `PROJECT_LEAD` | yes | yes | yes |
| `ADMIN` | yes | yes | yes |

---

### DELETE `/api/v1/admin/seed/projects/{projectId}/members/{userId}`

Remove a member from a project.

**Response `204`**

---

### POST `/api/v1/admin/seed/reset`

Reset database seed data to Flyway defaults (`V2__seed.sql`): users, `proj_abc`, sample issues, sprints `sprint_10` / `sprint_11`, etc. Invalidates the `proj_abc` board cache.

**Response `200`** — same shape as `GET /seed` snapshot.

---

## Examples

```bash
BASE=http://localhost:8001
KEY=demo-admin-key   # or your Render ADMIN_API_KEY

# Snapshot
curl "$BASE/api/v1/admin/seed" -H "X-Admin-Key: $KEY"

# Add user
curl -X POST "$BASE/api/v1/admin/seed/users" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"user_reviewer","email":"reviewer@example.com","displayName":"Reviewer"}'

# Create project
curl -X POST "$BASE/api/v1/admin/seed/projects" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"proj_demo","key":"DEMO","name":"Demo Team","adminUserId":"user_admin"}'

# Add member
curl -X POST "$BASE/api/v1/admin/seed/projects/proj_demo/members" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"userId":"user_reviewer","role":"MEMBER"}'

# Reset to defaults
curl -X POST "$BASE/api/v1/admin/seed/reset" -H "X-Admin-Key: $KEY"

# Alternative: use platform admin user (no API key)
curl "$BASE/api/v1/admin/seed" -H "X-User-Id: user_admin"
```

---

## Typical demo setup flow

1. `GET /api/v1/admin/seed` — inspect current state
2. `POST /api/v1/admin/seed/projects` — create a demo project
3. `POST /api/v1/admin/seed/users` + `POST .../members` — add reviewers or stakeholders
4. Use regular API with `X-User-Id` to create issues, run sprints, etc.
5. `POST /api/v1/admin/seed/reset` — restore defaults between demos

---

## Default seed data

After migration / reset, these IDs are available:

| Resource | IDs |
|----------|-----|
| Users | `user_admin`, `user_lead`, `user_member`, `user_viewer` |
| Project | `proj_abc` (key `PROJ`) |
| Sprints | `sprint_10` (ACTIVE), `sprint_11` (PLANNED) |
| Sample issues | `PROJ-100`, `PROJ-123`, `PROJ-124` |

See also: [Postman collection](../postman/Project-Board.postman_collection.json) for importable request flows.
