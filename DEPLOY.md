# Railway Deployment Guide

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

### Required Environment Variables

| Variable | Example | Notes |
|---|---|---|
| `DATABASE_URL` | `jdbc:postgresql://postgres.railway.internal:5432/railway` | Railway private network JDBC URL |
| `DATABASE_USER` | `postgres` | PostgreSQL user provisioned by Railway plugin |
| `DATABASE_PASSWORD` | `<secret>` | PostgreSQL password provisioned by Railway plugin |

### How to get PostgreSQL values

In the Railway dashboard, open the PostgreSQL plugin → **Connect** tab. Use the **Private** connection string. Extract:
- `DATABASE_URL` = JDBC form: `jdbc:postgresql://<host>:<port>/<db>`
- `DATABASE_USER` = `PGUSER` value
- `DATABASE_PASSWORD` = `PGPASSWORD` value

---

## Frontend Service

**Root directory:** `frontend/`  
**Builder:** Dockerfile  
**Start command:** (set by Dockerfile `CMD`)

### Required Environment Variables

| Variable | Example | Notes |
|---|---|---|
| `BACKEND_URL` | `http://backend.railway.internal:8080` | Railway private network URL for backend service |
| `SESSION_SECRET` | `<random 32+ char string>` | Cookie signing secret — generate with `openssl rand -hex 32` |
| `NODE_ENV` | `production` | Must be `production` to enforce SESSION_SECRET check |
| `PORT` | `3000` | Port the Node.js server listens on |

### How to get BACKEND_URL

In the Railway dashboard, open the **backend** service → **Settings** → **Networking**. Copy the **Private URL** (format: `http://backend.railway.internal:8080`).

---

## Internal Networking Pattern

Railway's private network lets services communicate without going through the public internet:

```
Browser → frontend (public HTTPS) → backend (private HTTP) → PostgreSQL (private TCP)
```

- The `BACKEND_URL` env var in the frontend points to the backend's Railway internal hostname.
- The backend's CORS is disabled (`%prod.quarkus.http.cors=false`) because the browser never sends cross-origin requests to it directly.
- PostgreSQL is accessible only from within the Railway project network.

---

## Deployment Steps

1. Create a new Railway project.
2. Add a **PostgreSQL** plugin — Railway provisions credentials automatically.
3. Add a service from the repo for `backend/` — set env vars from the table above.
4. Add a service from the repo for `frontend/` — set `BACKEND_URL` to the backend's private URL once the backend service is deployed.
5. Set `SESSION_SECRET` on the frontend service using `openssl rand -hex 32`.
6. Deploy both services. Verify:
   - `GET https://<backend-public-url>/q/health` returns `{"status":"UP"}`
   - `GET https://<frontend-public-url>/` renders the login page

---

## Health Checks

| Service | Endpoint | Expected |
|---|---|---|
| backend | `/q/health` | `{"status":"UP"}` |
| backend | `/q/health/live` | `{"status":"UP"}` |
| backend | `/q/health/ready` | `{"status":"UP"}` |

Configure Railway health probes to use `/q/health/live` for liveness and `/q/health/ready` for readiness.
