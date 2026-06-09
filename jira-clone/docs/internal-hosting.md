# Internal demo hosting

Host Project Board on your **company/internal network** so teammates can try the API without public internet access.

## What you need

| Item | Example |
|------|---------|
| Internal Linux server or VM | `10.0.1.50` or `demo-server.internal` |
| Docker + Docker Compose | v24+ |
| Network access | Colleagues on same VPN/LAN can reach port **8001** |
| Git | Clone this repo on the server |

---

## Step 1 — Prepare the server

```bash
# On the internal demo server
git clone <your-repo-url> jira-clone
cd jira-clone
```

Install Docker if needed: https://docs.docker.com/engine/install/

---

## Step 2 — Configure environment

Copy the example env file and edit it:

```bash
cp .env.demo.example .env
```

Set at minimum:

```bash
# Internal URL colleagues will use (no trailing slash)
PUBLIC_BASE_URL=http://10.0.1.50:8001

# Secret for admin seed APIs — change this!
ADMIN_API_KEY=your-internal-demo-secret

# Optional: stronger DB password for shared servers
POSTGRES_PASSWORD=choose-a-strong-password
```

Find the server IP:

```bash
hostname -I | awk '{print $1}'
# or
ip addr show
```

---

## Step 3 — Start the demo stack

Use the **demo compose file** (only exposes port 8001; Postgres/Redis stay internal to Docker):

```bash
docker compose -f docker-compose.demo.yml up -d --build
```

Check health:

```bash
curl http://localhost:8001/api/health/live
curl http://localhost:8001/api/health/ready
```

---

## Step 4 — Share with your team

Give reviewers these links (replace with your server IP or internal DNS name):

| Resource | URL |
|----------|-----|
| Swagger UI | `http://10.0.1.50:8001/api/docs` |
| Health | `http://10.0.1.50:8001/api/health/live` |
| Board API | `http://10.0.1.50:8001/api/v1/projects/proj_abc/board` |
| WebSocket | `ws://10.0.1.50:8001/ws/board?project_id=proj_abc&user_id=user_lead` |

**Auth header** (required on all `/api/v1/*` calls):

```
X-User-Id: user_member
```

Seed users: `user_admin`, `user_lead`, `user_member`, `user_viewer`

Example for teammates:

```bash
curl http://10.0.1.50:8001/api/v1/projects/proj_abc/board \
  -H "X-User-Id: user_member"
```

---

## Step 5 — Firewall (internal only)

Allow **only port 8001** from your internal subnet. Do **not** expose Postgres (5432) or Redis (6379) outside Docker.

**Linux (ufw) example:**

```bash
sudo ufw allow from 10.0.0.0/8 to any port 8001
sudo ufw enable
```

**AWS security group:** inbound TCP 8001 from corporate VPN CIDR only.

---

## Optional — Internal DNS name

Ask IT to create a record, e.g.:

```
project-board.demo.company.internal  →  10.0.1.50
```

Then set:

```bash
PUBLIC_BASE_URL=http://project-board.demo.company.internal:8001
docker compose -f docker-compose.demo.yml up -d --build app
```

---

## Optional — HTTPS with nginx (internal CA)

If your org has an internal certificate:

```nginx
server {
    listen 443 ssl;
    server_name project-board.demo.company.internal;

    ssl_certificate     /etc/ssl/internal/demo.crt;
    ssl_certificate_key /etc/ssl/internal/demo.key;

    location / {
        proxy_pass http://127.0.0.1:8001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection "upgrade";
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

WebSocket URL becomes:

```
wss://project-board.demo.company.internal/ws/board?project_id=proj_abc&user_id=user_lead
```

Set `PUBLIC_BASE_URL=https://project-board.demo.company.internal`

---

## Admin / seed setup for demo

Reset or customize demo data:

```bash
BASE=http://10.0.1.50:8001
KEY=your-internal-demo-secret

# List current seed
curl "$BASE/api/v1/admin/seed" -H "X-Admin-Key: $KEY"

# Create a new project for demo
curl -X POST "$BASE/api/v1/admin/seed/projects" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"proj_demo","key":"DEMO","name":"Demo Team","adminUserId":"user_admin"}'

## Admin seed APIs

Protected by **either** `X-Admin-Key: <ADMIN_API_KEY>` or `X-User-Id: user_admin`.

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/admin/seed` | List users, projects, members |
| POST | `/api/v1/admin/seed/users` | Add user |
| DELETE | `/api/v1/admin/seed/users/{userId}` | Remove user |
| POST | `/api/v1/admin/seed/projects` | Create project (default workflow) |
| DELETE | `/api/v1/admin/seed/projects/{projectId}` | Remove project |
| POST | `/api/v1/admin/seed/projects/{projectId}/members` | Add member |
| DELETE | `/api/v1/admin/seed/projects/{projectId}/members/{userId}` | Remove member |
| POST | `/api/v1/admin/seed/reset` | Reset demo data to defaults |

Add user and project:

```bash
curl -X POST "$BASE/api/v1/admin/seed/users" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"user_reviewer","email":"reviewer@example.com","displayName":"Reviewer"}'

curl -X POST "$BASE/api/v1/admin/seed/projects" \
  -H "X-Admin-Key: $KEY" \
  -H "Content-Type: application/json" \
  -d '{"id":"proj_demo","key":"DEMO","name":"Demo Team","adminUserId":"user_admin"}'
```

Remove project or user:

```bash
curl -X DELETE "$BASE/api/v1/admin/seed/projects/proj_demo" -H "X-Admin-Key: $KEY"
curl -X DELETE "$BASE/api/v1/admin/seed/users/user_reviewer" -H "X-Admin-Key: $KEY"
```

---

## Operations

```bash
# View logs
docker compose -f docker-compose.demo.yml logs -f app

# Restart after config change
docker compose -f docker-compose.demo.yml up -d --build app

# Stop demo
docker compose -f docker-compose.demo.yml down

# Stop and wipe database (fresh start)
docker compose -f docker-compose.demo.yml down -v
```

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Colleagues can't connect | Check firewall, VPN, and that they use server IP not `localhost` |
| `connection refused` on 8001 | Run `docker compose ps` — app container must be `healthy`/running |
| Swagger shows wrong server | Set `PUBLIC_BASE_URL` and restart app |
| WebSocket fails through nginx | Enable `Upgrade` / `Connection` headers (see nginx example above) |
| Database empty after restart | Without `-v`, data persists in Docker volume `pgdata` |

---

## Quick checklist for interview / demo submission

1. [ ] App running on internal server (`docker compose -f docker-compose.demo.yml up -d --build`)
2. [ ] `curl http://<internal-ip>:8001/api/health/live` returns `UP`
3. [ ] Swagger reachable at `http://<internal-ip>:8001/api/docs`
4. [ ] `PUBLIC_BASE_URL` set to internal URL
5. [ ] `ADMIN_API_KEY` changed from default
6. [ ] Share seed user IDs + sample curl with reviewers
