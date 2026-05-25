---
id: S08
parent: M001
milestone: M001
provides:
  - Deployable Quarkus JAR with Railway health probes at /q/health
  - SvelteKit Node.js server bundle built with adapter-node
  - Production env-var-driven backend DB config via %prod profile
  - Configurable BACKEND_URL proxy in SvelteKit catch-all route
  - Lazy SESSION_SECRET enforcement safe for vite build
  - Multi-stage backend/Dockerfile building from source
  - Multi-stage frontend/Dockerfile for Railway Node deployment
  - DEPLOY.md deployment checklist with all required env vars
requires:
  - slice: S01
    provides: SvelteKit proxy pattern and session management
  - slice: S02
    provides: Category and contact domain models
  - slice: S03
    provides: Transaction domain model
  - slice: S04
    provides: Dashboard aggregation service
  - slice: S05
    provides: Subscription domain with billing scheduler
  - slice: S06
    provides: Debt and partial payment domain
  - slice: S07
    provides: Public token-based subscription view
affects:
  []
key_files:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/Dockerfile
  - frontend/svelte.config.js
  - frontend/src/routes/api/[...path]/+server.ts
  - frontend/src/lib/server/session.ts
  - frontend/Dockerfile
  - frontend/package.json
  - DEPLOY.md
  - frontend/src/routes/categories/+page.svelte
key_decisions:
  - Used %prod profile prefix so dev config (localhost DB) remains the default — no env vars needed for local development
  - Disabled CORS in prod (%prod.quarkus.http.cors=false) because Railway private network means SvelteKit proxies all backend calls — no browser-to-Quarkus direct requests
  - SESSION_SECRET check made lazy (inside getSessionSecret() at request time) — SvelteKit post-build analysis imports server modules with NODE_ENV=production, so eager throw breaks vite build
  - Multi-stage Dockerfiles build from source so Railway only needs the Git repo, not pre-built artifacts
  - Frontend build script simplified to 'vite build' only — prepack chain ran svelte-package/publint (library tooling) incompatible with app deployment
patterns_established:
  - Env-var-driven production config via Quarkus %prod profile prefix (zero env vars needed for local dev)
  - Lazy secret enforcement pattern: validate secrets inside request-path functions, not at module load time
observability_surfaces:
  - Quarkus health endpoint at /q/health (liveness + readiness) for Railway health probes
  - Structured startup error when SESSION_SECRET is missing (thrown at first request, not silently ignored)
  - Maven build output shows dependency resolution failures explicitly when run without -q flag
drill_down_paths:
  - .gsd/milestones/M001/slices/S08/tasks/T01-SUMMARY.md
  - .gsd/milestones/M001/slices/S08/tasks/T02-SUMMARY.md
  - .gsd/milestones/M001/slices/S08/tasks/T03-SUMMARY.md
duration: ""
verification_result: passed
completed_at: 2026-05-14T20:51:19.176Z
blocker_discovered: false
---

# S08: Railway Deployment

**Both services build into deployable Railway artifacts — Quarkus JAR with health probes, SvelteKit Node bundle with env-var-driven proxy and session config, plus a deployment checklist in DEPLOY.md**

## What Happened

S08 hardened both services for production deployment on Railway across three tasks.

**T01 — Backend production configuration:** Added `quarkus-smallrye-health` to pom.xml for Railway health probes at `/q/health`. Created a `%prod` profile in `application.properties` that reads all DB coordinates from `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD` env vars, leaving the dev profile (localhost defaults) intact so no env vars are needed for local development. Disabled CORS in prod (`%prod.quarkus.http.cors=false`) because Railway's private network means the SvelteKit frontend proxies all backend calls — no browser ever hits Quarkus directly. Created a multi-stage `backend/Dockerfile` that builds from source with `./mvnw package -DskipTests` so Railway only needs the Git repo, not a pre-built artifact.

**T02 — Frontend production configuration:** Switched from `adapter-auto` to `adapter-node` in `svelte.config.js` for Railway's Node.js runtime. Made the backend proxy URL configurable via `BACKEND_URL` env var in the `[...path]/+server.ts` catch-all proxy route. Enforced `SESSION_SECRET` lazily inside `getSessionSecret()` (called at request time) rather than at module load time — this was required because SvelteKit's post-build analysis imports server modules with `NODE_ENV=production`, so an eager throw breaks `vite build`. Fixed the `package.json` build script to `vite build` only, removing a `prepack` chain that ran `svelte-package` and `publint` (library tooling incompatible with app deployment). Also installed `@fontsource-variable/fraunces`, a missing font dependency referenced in `layout.css` that caused build failure. Created a multi-stage `frontend/Dockerfile`.

**T03 — End-to-end build verification:** Ran both full builds (`./mvnw package -DskipTests` and `bun run build`) and confirmed both artifact trees exist (`backend/target/quarkus-app`, `frontend/build`). Fixed a pre-existing TypeScript error in `categories/+page.svelte` where `superForm()` was called with a getter function wrapping `data.form` — Svelte 5 `$props()` returns are already reactive, so the wrapper broke the TypeScript signature. Wrote `DEPLOY.md` documenting all Railway env vars (`DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `BACKEND_URL`, `SESSION_SECRET`), service topology, health probe configuration, and deployment steps.

## Verification

All verification checks passed fresh in the current session:

- `cd backend && ./mvnw package -DskipTests -q` → exit 0 (4.5s, incremental build)
- `cd frontend && bun run build` → exit 0 (6.3s)
- `test -f DEPLOY.md` → ✓
- `test -d backend/target/quarkus-app` → ✓
- `test -d frontend/build` → ✓
- `grep -q 'DATABASE_URL' DEPLOY.md` → ✓
- `grep -q 'SESSION_SECRET' DEPLOY.md` → ✓
- `grep -q 'BACKEND_URL' DEPLOY.md` → ✓
- `grep -q 'smallrye-health' backend/pom.xml` → ✓
- `grep -q 'DATABASE_URL' backend/src/main/resources/application.properties` → ✓
- `grep -q 'adapter-node' frontend/svelte.config.js` → ✓
- `grep -q 'BACKEND_URL' frontend/src/routes/api/[...path]/+server.ts` → ✓
- `test -f backend/Dockerfile` → ✓
- `test -f frontend/Dockerfile` → ✓

## Requirements Advanced

None.

## Requirements Validated

None.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

["Fixed pre-existing TypeScript error in frontend/src/routes/categories/+page.svelte: superForm() was called with a getter function wrapping data.form — Svelte 5 $props() returns are already reactive, so the getter wrapper broke the TypeScript signature. Required to satisfy bun run check exit 0.", "Installed @fontsource-variable/fraunces — referenced in layout.css but absent from package.json, causing bun run build to fail. Not in the original task plan."]

## Known Limitations

["Full runtime verification (live DB connection, HTTPS, Railway health probe timing) requires Railway dashboard setup — outside code scope", "Flyway migration execution against Railway PostgreSQL not verified — happens on first Quarkus startup in Railway", "Mobile Safari accessibility on the deployed URL not verified in this slice"]

## Follow-ups

["Create Railway services for backend and frontend via Railway dashboard", "Set all env vars documented in DEPLOY.md in Railway service settings", "Provision Railway PostgreSQL plugin and wire DATABASE_URL to backend service", "Verify /q/health responds 200 after Railway deployment", "Test full app on mobile Safari via Railway HTTPS URL"]

## Files Created/Modified

None.
