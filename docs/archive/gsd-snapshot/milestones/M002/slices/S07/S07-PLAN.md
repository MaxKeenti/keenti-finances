# S07: Railway Deployment & Production Verification

**Goal:** App deployed to Railway with all M002 features functional; pre-deploy builds verified clean, deployment checklist documented with all env vars and WorkOS configuration steps, and production smoke tests defined for manual execution.
**Demo:** App deployed to Railway with all M002 features functional; manual billing trigger and theme detection verified in production

## Must-Haves

- Backend builds and 12 integration tests pass; frontend vite build and svelte-check pass; deployment checklist covers ORIGIN, WorkOS env vars, redirect URI, and Flyway migration expectations.

## Proof Level

- This slice proves: operational — real runtime required (Railway deployment); human/UAT required (Railway dashboard config, WorkOS dashboard config, production browser verification)

## Integration Closure

Upstream surfaces consumed: all S01-S06 outputs — dock layout, theme detection, category colors, billing endpoints, transaction linking, WorkOS auth flow, backend tests. New wiring introduced: none (deployment uses existing Dockerfiles and railway.toml). What remains before milestone is truly usable end-to-end: Railway dashboard env var configuration, WorkOS redirect URI registration, and production smoke test pass.

## Verification

- Flyway migration logs (V7, V8, V9 applied), backend /q/health/live, frontend /health. Railway service logs surface startup errors. SvelteKit ORIGIN mismatch visible as WorkOS OAuth 400 on /callback.

## Tasks

- [x] **T01: Pre-deploy build and test verification** `est:30m`
  Why: Before deploying to Railway, all builds and tests must pass locally to catch issues before they hit production. A failed build in the Dockerfile wastes deploy time and Railway build minutes.
  - Files: `backend/pom.xml`, `frontend/package.json`, `backend/src/main/resources/db/migration/V7__add_color_to_category.sql`, `backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql`, `backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql`, `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java`
  - Verify: ./mvnw test -f backend/pom.xml

- [x] **T02: Write Railway deployment checklist with env var manifest** `est:20m`
  Why: The executor agent cannot access Railway or WorkOS dashboards. A deployment checklist ensures the human deployer has a single reference for all configuration steps, env vars, and verification checks needed to get M002 live. The ORIGIN env var is the highest-risk item (silent misconfiguration breaks auth). This checklist is the operational artifact that bridges agent work to human deployment.
  - Files: `DEPLOY-M002.md`
  - Verify: test -f DEPLOY-M002.md

## Files Likely Touched

- backend/pom.xml
- frontend/package.json
- backend/src/main/resources/db/migration/V7__add_color_to_category.sql
- backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql
- backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql
- backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java
- DEPLOY-M002.md
