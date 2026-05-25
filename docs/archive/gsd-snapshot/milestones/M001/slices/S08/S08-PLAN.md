# S08: Railway Deployment

**Goal:** Full app running on Railway with HTTPS — Quarkus backend and SvelteKit frontend deployed as separate services, PostgreSQL provisioned via Railway plugin, all features accessible from the internet on mobile Safari.
**Demo:** Full app running on Railway with HTTPS, PostgreSQL provisioned, all features accessible from the internet on mobile Safari

## Must-Haves

- 1. `./mvnw package -DskipTests` produces a runnable Quarkus JAR with production DB config from env vars. 2. `bun run build` produces a Node.js server bundle using adapter-node. 3. Backend Dockerfile builds successfully. 4. Frontend Dockerfile builds successfully. 5. Proxy backend URL is configurable via BACKEND_URL env var (no hardcoded localhost). 6. SESSION_SECRET throws on startup if unset in production. 7. Quarkus health endpoint responds at /q/health.

## Proof Level

- This slice proves: operational — both services must build into deployable artifacts with production-safe configuration; full runtime proof requires Railway infrastructure

## Integration Closure

Upstream surfaces consumed: all S01–S07 application code, Flyway migrations V1–V5, SvelteKit proxy at `frontend/src/routes/api/[...path]/+server.ts`, session management at `frontend/src/lib/server/session.ts`, `backend/src/main/resources/application.properties`. New wiring: Dockerfiles for both services, adapter-node for SvelteKit, env-var-driven configuration for DB/proxy/session. What remains: Railway dashboard service creation and env var population (manual, outside code scope).

## Verification

- Quarkus health check at /q/health (liveness + readiness) for Railway health probes. Structured startup logs for missing env vars (SESSION_SECRET, BACKEND_URL) so deployment failures are immediately diagnosable.

## Tasks

- [x] **T01: Configure Quarkus backend for production deployment** `est:45m`
  Add production profile to application.properties with env-var-based DB configuration, add the quarkus-smallrye-health extension for Railway health probes, and create a root-level Dockerfile in backend/ based on the existing Dockerfile.jvm template.
  - Files: `backend/pom.xml`, `backend/src/main/resources/application.properties`, `backend/Dockerfile`
  - Verify: ./mvnw compile -q && grep -q 'smallrye-health' backend/pom.xml && grep -q 'DATABASE_URL' backend/src/main/resources/application.properties && test -f backend/Dockerfile

- [x] **T02: Configure SvelteKit frontend for production deployment** `est:45m`
  Switch from adapter-auto to adapter-node for Railway Node.js deployment, make the backend proxy URL configurable via BACKEND_URL env var, enforce SESSION_SECRET in production, and create a Dockerfile.
  - Files: `frontend/package.json`, `frontend/svelte.config.js`, `frontend/src/routes/api/[...path]/+server.ts`, `frontend/src/lib/server/session.ts`, `frontend/Dockerfile`
  - Verify: cd frontend && bun run build && grep -q 'adapter-node' svelte.config.js && grep -q 'BACKEND_URL' src/routes/api/\[...path\]/+server.ts && test -f Dockerfile

- [x] **T03: Full build verification and deployment readiness check** `est:30m`
  Verify both services build cleanly end-to-end: backend Maven package, frontend bun build, both Dockerfiles parse correctly, and all env var references are documented in a deployment checklist.
  - Files: `DEPLOY.md`
  - Verify: cd backend && ./mvnw package -DskipTests -q && cd ../frontend && bun run build && test -f ../DEPLOY.md

## Files Likely Touched

- backend/pom.xml
- backend/src/main/resources/application.properties
- backend/Dockerfile
- frontend/package.json
- frontend/svelte.config.js
- frontend/src/routes/api/[...path]/+server.ts
- frontend/src/lib/server/session.ts
- frontend/Dockerfile
- DEPLOY.md
