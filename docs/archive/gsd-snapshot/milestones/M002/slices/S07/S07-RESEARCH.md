# S07 Research: Railway Deployment & Production Verification

## Summary

S07 is a **light research** slice. The deployment infrastructure (Dockerfiles, railway.toml, catch-all proxy, health endpoints, `%prod` Quarkus profile) already exists from M001. The work is: ensure new M002 migrations/features deploy cleanly, configure WorkOS env vars on Railway, set `ORIGIN` for SvelteKit adapter-node, and verify key features in production.

## Recommendation

Execute as config + verification — no new code architecture needed. The main risks are (1) missing the `ORIGIN` env var for SvelteKit behind Railway's HTTPS proxy, (2) WorkOS redirect URI configuration matching the Railway domain, and (3) Flyway migrations V7–V9 applying cleanly against the production PostgreSQL schema.

## Implementation Landscape

### What Already Exists

| Artifact | Path | Status |
|---|---|---|
| Backend Dockerfile | `backend/Dockerfile` | Multi-stage UBI9 + OpenJDK 21; builds with `./mvnw package -DskipTests` |
| Frontend Dockerfile | `frontend/Dockerfile` | Bun builder → Node 22 runner; `bun run build` then `node build/index.js` |
| Backend railway.toml | `backend/railway.toml` | Healthcheck at `/q/health/live`, 300s timeout |
| Frontend railway.toml | `frontend/railway.toml` | Healthcheck at `/health`, 120s timeout |
| Frontend health endpoint | `frontend/src/routes/health/+server.ts` | Returns `{"status":"UP"}`; listed in PUBLIC_PATHS |
| Quarkus %prod profile | `backend/src/main/resources/application.properties` | `DATABASE_URL`, `DATABASE_USER`, `DATABASE_PASSWORD` from env; CORS disabled |
| SvelteKit proxy | `frontend/src/routes/api/[...path]/+server.ts` | Proxies to `BACKEND_URL` with 30s timeout |
| SvelteKit adapter | `frontend/svelte.config.js` | `@sveltejs/adapter-node` configured |

### Flyway Migrations to Apply (new in M002)

| Migration | Content | Risk |
|---|---|---|
| V7 | `ALTER TABLE category ADD COLUMN color VARCHAR(10)` | Low — nullable ADD COLUMN |
| V8 | `ADD owner_participates BOOLEAN DEFAULT TRUE` to subscription + `ADD subscription_id BIGINT NULL FK` to transaction | Low — nullable/defaulted columns |
| V9 | `ALTER TABLE app_user ALTER COLUMN password_hash DROP NOT NULL` | Low — relaxing constraint, not tightening |

All three are non-destructive, additive schema changes. No data migration or backfill needed.

### Environment Variables Required on Railway

**Frontend service:**

| Variable | Source | Notes |
|---|---|---|
| `BACKEND_URL` | Railway private networking URL | Already configured from M001; e.g. `http://backend.railway.internal:8080` |
| `WORKOS_API_KEY` | WorkOS dashboard | **New for M002** — lazy-loaded via `getWorkOS()` singleton |
| `WORKOS_CLIENT_ID` | WorkOS dashboard | **New for M002** — used in hooks.server.ts and callback |
| `WORKOS_COOKIE_PASSWORD` | User-generated 32+ char secret | **New for M002** — AES-256-GCM key derivation |
| `ORIGIN` | Railway public URL (e.g. `https://keenti.up.railway.app`) | **CRITICAL — not currently set.** SvelteKit adapter-node needs this to resolve `url.origin` correctly behind Railway's HTTPS termination proxy. Without it, `url.origin` returns `http://...` and WorkOS redirect URI mismatches will fail the OAuth callback. |
| `NODE_ENV` | `production` | Already set in Dockerfile |
| `PORT` | `3000` | Already set in Dockerfile; Railway auto-detects |

**Backend service:**

| Variable | Source | Notes |
|---|---|---|
| `DATABASE_URL` | Railway PostgreSQL plugin | Already configured from M001 via `%prod` profile |
| `DATABASE_USER` | Railway PostgreSQL plugin | Already configured |
| `DATABASE_PASSWORD` | Railway PostgreSQL plugin | Already configured |

### WorkOS Configuration Requirements

1. **Redirect URI**: Must register `https://<railway-domain>/callback` in WorkOS dashboard (Authentication → Redirect URIs)
2. **Passkey domain**: Passkeys are domain-bound — the Railway domain (or custom domain) used for initial passkey registration becomes permanent. Switching domains later invalidates all registered passkeys.
3. **AuthKit provider**: Must be enabled in WorkOS dashboard with passkey authentication method selected.

### Key Risk: ORIGIN Env Var

SvelteKit's adapter-node documentation states: *"If your app is behind a reverse proxy, you'll need to set the `ORIGIN` environment variable."* Railway terminates HTTPS at the edge and forwards HTTP to the container. Without `ORIGIN`, `event.url.origin` returns `http://localhost:3000` instead of `https://keenti.up.railway.app`, causing:
- WorkOS OAuth redirect URI mismatch → 400 error on callback
- Incorrect redirect URIs in auth flow

This is the highest-risk item because it's a silent misconfiguration — the app starts fine but auth fails at runtime.

## Natural Seams (Task Decomposition Guidance)

### T01: Pre-deploy verification (build + test)
- Run `./mvnw package -DskipTests` in backend/ to verify Dockerfile build step works
- Run `bun run build` in frontend/ to verify Dockerfile build step works
- Run `./mvnw test` in backend/ to confirm 12 integration tests pass
- Run `npx svelte-check --threshold error` to confirm no new errors
- **Files:** None modified — verification only
- **Verify:** All four commands exit 0

### T02: Configure Railway environment variables
- Set `WORKOS_API_KEY`, `WORKOS_CLIENT_ID`, `WORKOS_COOKIE_PASSWORD` on frontend service
- Set `ORIGIN` to the Railway public URL on frontend service
- Verify `BACKEND_URL` is still correctly set for private networking
- Configure WorkOS dashboard: add redirect URI, enable AuthKit with passkeys
- **Files:** None in repo — Railway dashboard + WorkOS dashboard configuration
- **Verify:** `railway variables` shows all expected vars (or manual dashboard check)

### T03: Deploy and verify migrations
- Push to Railway (or trigger deploy from dashboard)
- Watch backend logs for Flyway V7, V8, V9 migration success
- Confirm backend healthcheck passes at `/q/health/live`
- Confirm frontend healthcheck passes at `/health`
- **Files:** None modified
- **Verify:** Both services healthy in Railway dashboard; Flyway logs show 3 new migrations applied

### T04: Production smoke tests
- Verify theme detection: page loads with correct light/dark mode matching device preference
- Verify manual billing trigger: click Generate Billing button → toast shows record count
- Verify passkey auth: complete passkey registration and login flow
- Verify dock navigation: all icons visible on desktop, overflow menu works on mobile
- Verify category badges: colors render correctly in both themes
- **Files:** None modified — browser verification
- **Verify:** Each feature manually tested in production browser

## First Proof

T01 (pre-deploy build verification) is the unblocker — if builds fail, nothing else matters. T02 (env var configuration) is the highest-risk item because `ORIGIN` misconfiguration silently breaks auth.

## Constraints

- Passkeys are domain-bound: the production domain chosen for initial registration is permanent
- V6 migration sets admin password hash — V9 makes it nullable, which is compatible but means the old password is irrelevant (WorkOS handles auth now)
- Frontend Dockerfile uses Bun for build but Node 22 for runtime — `@workos-inc/node` must be in production dependencies (it's in `dependencies`, not `devDependencies` — confirmed by Dockerfile's `npm install --omit=dev`)
- Railway's `healthcheckTimeout: 300` for backend is generous; Quarkus cold start with Flyway migrations should complete well within this
