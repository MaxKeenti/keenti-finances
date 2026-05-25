---
estimated_steps: 18
estimated_files: 1
skills_used: []
---

# T03: Full build verification and deployment readiness check

Verify both services build cleanly end-to-end: backend Maven package, frontend bun build, both Dockerfiles parse correctly, and all env var references are documented in a deployment checklist.

## Steps

1. Run `cd backend && ./mvnw package -DskipTests -q` — must exit 0 producing `target/quarkus-app/`.
2. Run `cd frontend && bun install && bun run build` — must exit 0 producing `build/` directory.
3. Run `cd frontend && bun run check` — must exit 0 with 0 errors.
4. Verify `backend/Dockerfile` has valid FROM/COPY/EXPOSE directives (grep checks).
5. Verify `frontend/Dockerfile` has valid FROM/COPY/EXPOSE directives (grep checks).
6. Create `DEPLOY.md` at project root documenting: Railway service setup (two services: backend + frontend, one PostgreSQL plugin), required env vars per service (backend: DATABASE_URL, DATABASE_USER, DATABASE_PASSWORD; frontend: BACKEND_URL, SESSION_SECRET, NODE_ENV=production, PORT=3000), and the Railway internal networking pattern (frontend BACKEND_URL points to backend's private URL).

## Must-Haves

- [ ] Backend packages into a runnable JAR
- [ ] Frontend builds into a Node.js server bundle
- [ ] Both Dockerfiles are structurally valid
- [ ] DEPLOY.md documents all env vars and Railway service topology

## Verification

- `test -d backend/target/quarkus-app`
- `test -d frontend/build`
- `test -f DEPLOY.md`
- `grep -q 'DATABASE_URL' DEPLOY.md && grep -q 'SESSION_SECRET' DEPLOY.md && grep -q 'BACKEND_URL' DEPLOY.md`

## Inputs

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/Dockerfile`
- `frontend/package.json`
- `frontend/svelte.config.js`
- `frontend/Dockerfile`
- `frontend/src/routes/api/[...path]/+server.ts`
- `frontend/src/lib/server/session.ts`

## Expected Output

- `DEPLOY.md`

## Verification

cd backend && ./mvnw package -DskipTests -q && cd ../frontend && bun run build && test -f ../DEPLOY.md
