# Railway Deployment Guide

Current deployment guide for Keenti Finances. Authentication is handled by the
SvelteKit frontend via WorkOS passkeys; the Quarkus backend is an internal-only
trusted API (no public ingress, no auth of its own).

> Historical milestone checklists live in `docs/archive/` (e.g. the M002 WorkOS
> cutover). This file describes the current desired state — start here.

## Service Topology

Two Railway services and one PostgreSQL plugin:

```
Railway Project
├── backend     (Quarkus JAR, port 8080)
├── frontend    (SvelteKit Node.js server, port 3000)
└── PostgreSQL  (Railway plugin — private network only)
```

The frontend proxies all `/api/*` requests to the backend over Railway's internal private network. The browser never talks to the backend directly.

---

## Backend Service

**Root directory:** `backend/`
**Builder:** Dockerfile
**Health check:** `GET /q/health` (liveness + readiness probes)

The backend is a trusted internal API. It has no authentication of its own —
tenant isolation is keyed off the `X-WorkOS-User-Id` header injected by the
frontend proxy. Never give the backend public ingress.

### Required Environment Variables

| Variable | Example | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://postgres.railway.internal:5432/railway` | Railway private network JDBC URL |
| `DATABASE_USER` | `postgres` | PostgreSQL user provisioned by Railway plugin |
| `DATABASE_PASSWORD` | `<secret>` | PostgreSQL password provisioned by Railway plugin |

No WorkOS env vars on the backend — authentication is handled entirely by the frontend.

### How to get PostgreSQL values

In the Railway dashboard, open the PostgreSQL plugin → **Connect** tab. Use the **Private** connection string. Extract:
- `DATABASE_URL` = JDBC form: `jdbc:postgresql://<host>:<port>/<db>`
- `DATABASE_USER` = `PGUSER` value
- `DATABASE_PASSWORD` = `PGPASSWORD` value

---

## Frontend Service

**Root directory:** `frontend/`
**Builder:** Dockerfile (bun build → Node.js runtime)
**Start command:** (set by Dockerfile `CMD`)

### Required Environment Variables

| Variable | Example | Notes |
|---|---|---|
| `BACKEND_URL` | `http://backend.railway.internal:8080` | Railway private network URL for backend service |
| `WORKOS_API_KEY` | `sk_...` | WorkOS Dashboard → API Keys. Server-side only — never expose to the client |
| `WORKOS_CLIENT_ID` | `client_...` | WorkOS Dashboard → API Keys |
| `WORKOS_COOKIE_PASSWORD` | `<random 32+ char string>` | Signs the encrypted WorkOS session cookie. Generate with `openssl rand -hex 32` (must be ≥ 32 chars) |
| `ORIGIN` | `https://your-app.up.railway.app` | **⚠️ CRITICAL** — SvelteKit builds the OAuth callback URL from this. Without it, WorkOS returns a redirect-URI-mismatch error and auth fails silently. No trailing slash. |
| `NODE_ENV` | `production` | Standard production flag for the Node adapter |
| `PORT` | `3000` | Port the Node.js server listens on |

> Session management uses WorkOS encrypted sessions (`WORKOS_COOKIE_PASSWORD`).
> There is no `SESSION_SECRET` — that was the pre-WorkOS custom cookie-signing
> secret and is no longer read anywhere.

### How to get BACKEND_URL

In the Railway dashboard, open the **backend** service → **Settings** → **Networking**. Copy the **Private URL** (format: `http://backend.railway.internal:8080`).

---

## WorkOS Dashboard Configuration

1. **Create a WorkOS account** at [workos.com](https://workos.com) if you haven't already.

2. **Get API credentials:**
   - Navigate to **API Keys** in the WorkOS dashboard.
   - Copy the **API Key** (`sk_...`) → set as `WORKOS_API_KEY`.
   - Copy the **Client ID** (`client_...`) → set as `WORKOS_CLIENT_ID`.

3. **Register the redirect URI:**
   - Go to **Redirects** in the WorkOS dashboard.
   - Add: `https://<your-frontend-domain>/callback`
   - This must exactly match what SvelteKit generates from the `ORIGIN` env var + `/callback`.

4. **Enable AuthKit with passkeys:**
   - Go to **Authentication** → **AuthKit**.
   - Enable **Passkeys** as an authentication method.
   - **Note:** Passkeys are bound to the domain they're registered on. If you change your frontend domain later, existing passkeys will stop working and users will need to re-register.

---

## Internal Networking Pattern

Railway's private network lets services communicate without going through the public internet:

```
Browser → frontend (public HTTPS) → backend (private HTTP) → PostgreSQL (private TCP)
```

- The `BACKEND_URL` env var in the frontend points to the backend's Railway internal hostname.
- The frontend's `handleFetch` injects `X-WorkOS-User-Id` on server-side fetches; the backend's `UserScopeFilter` resolves the tenant from that header.
- The backend's CORS is disabled (`%prod.quarkus.http.cors=false`) because the browser never sends cross-origin requests to it directly.
- PostgreSQL is accessible only from within the Railway project network.

---

## Flyway Migrations (Automatic)

Schema migrations in `backend/src/main/resources/db/migration` run automatically
on backend startup — no manual SQL execution needed. They are append-only; never
edit an applied migration (see `docs/agents/migrations.md`).

---

## Deployment Steps

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provisions credentials automatically.
3. Configure WorkOS per the section above (API keys, redirect URI, AuthKit passkeys).
4. Add a service from the repo for `backend/` — set the `DATABASE_*` env vars.
5. Add a service from the repo for `frontend/` — set `BACKEND_URL` to the backend's private URL, plus the WorkOS vars, `ORIGIN`, `NODE_ENV`, and `PORT`.
6. Deploy both services. Verify:
   - `GET https://<backend-public-url>/q/health` returns `{"status":"UP"}`
   - Navigating to the frontend redirects to WorkOS AuthKit; signing in / registering a passkey returns to the dashboard.

---

## Health Checks

| Service | Endpoint | Expected |
|---|---|---|
| backend | `/q/health` | `{"status":"UP"}` |
| backend | `/q/health/live` | `{"status":"UP"}` |
| backend | `/q/health/ready` | `{"status":"UP"}` |
| frontend | `/health` | `{"status":"UP"}` |

Configure Railway health probes to use `/q/health/live` for backend liveness and `/q/health/ready` for readiness.

**Frontend healthcheck path:** Set the Railway healthcheck path to `/health` for the frontend service. The default `/` path returns a 303 redirect (unauthenticated visitors are sent to WorkOS sign-in), which Railway would interpret as unhealthy.
