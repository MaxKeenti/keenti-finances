# S08: Railway Deployment — UAT

**Milestone:** M001
**Written:** 2026-05-14T20:51:19.177Z

# S08: Railway Deployment — UAT

**Milestone:** M001
**Written:** 2026-05-14

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: The slice proof level is explicitly "operational" — both services must build into deployable artifacts with production-safe configuration. Full runtime proof (live traffic, PostgreSQL connection, HTTPS) requires Railway infrastructure that is outside code scope. Artifact-driven UAT confirms deployability; live verification is the next step after Railway service creation.

## Preconditions

- Repository cloned to a machine with JDK 21, Maven wrapper (`./mvnw`), and Bun installed
- No Railway account required for artifact verification
- No `.env` files or environment variables needed for build-time checks

## Smoke Test

Run both builds from the repo root:
```
cd backend && ./mvnw package -DskipTests -q && echo "Backend OK"
cd ../frontend && bun run build && echo "Frontend OK"
```
Both should print their respective "OK" and exit 0.

## Test Cases

### 1. Backend JAR builds with health extension

1. `cd backend`
2. `./mvnw package -DskipTests -q`
3. `test -d target/quarkus-app && echo "artifact present"`
4. `grep -q 'quarkus-smallrye-health' pom.xml && echo "health extension wired"`
5. **Expected:** All four commands exit 0; `target/quarkus-app/` directory exists with `quarkus-run.jar`

### 2. Frontend Node bundle builds cleanly

1. `cd frontend`
2. `bun run build`
3. `test -d build && echo "build artifact present"`
4. `grep -q 'adapter-node' svelte.config.js && echo "adapter-node wired"`
5. **Expected:** Build exits 0 (circular dependency warnings from `zod-v3-to-json-schema` are benign); `build/` directory contains `index.js` server entry

### 3. Backend production env var configuration

1. `grep '%prod.quarkus.datasource.jdbc.url' backend/src/main/resources/application.properties`
2. `grep 'DATABASE_URL' backend/src/main/resources/application.properties`
3. **Expected:** Lines present showing `${DATABASE_URL}`, `${DATABASE_USERNAME}`, `${DATABASE_PASSWORD}` references under `%prod` profile

### 4. Frontend proxy uses BACKEND_URL

1. `grep 'BACKEND_URL' frontend/src/routes/api/\[...path\]/+server.ts`
2. **Expected:** Line present; no hardcoded `localhost` URL in the proxy fetch call

### 5. SESSION_SECRET enforced lazily in production

1. `grep 'SESSION_SECRET' frontend/src/lib/server/session.ts`
2. `grep 'getSessionSecret' frontend/src/lib/server/session.ts`
3. **Expected:** `SESSION_SECRET` referenced inside `getSessionSecret()` function body; no top-level throw that would break `vite build`

### 6. DEPLOY.md documents all required env vars

1. `cat DEPLOY.md`
2. **Expected:** File documents `DATABASE_URL`, `DATABASE_USERNAME`, `DATABASE_PASSWORD`, `BACKEND_URL`, `SESSION_SECRET` with descriptions of where each is set in the Railway dashboard

### 7. Dockerfiles are present and structurally valid

1. `grep -E 'FROM|COPY|EXPOSE' backend/Dockerfile`
2. `grep -E 'FROM|COPY|EXPOSE' frontend/Dockerfile`
3. **Expected:** Both files contain multi-stage FROM instructions, COPY steps, and EXPOSE declarations

## Edge Cases

### SESSION_SECRET missing at runtime (not build time)

1. Deploy backend and frontend without setting `SESSION_SECRET` in Railway
2. Make a request that triggers session creation (e.g., POST /api/auth/login)
3. **Expected:** Server throws an error with a clear message about missing `SESSION_SECRET`; build succeeds even without the var set

### Dev mode still works without env vars

1. `cd backend && ./mvnw quarkus:dev` (no DB env vars set)
2. **Expected:** Quarkus starts using the default dev profile (localhost DB); no prod env vars required for local development

## Failure Signals

- Maven build prints `BUILD FAILURE` — indicates missing dependency, compilation error, or broken pom.xml
- `bun run build` prints error lines before `✔ done` — indicates TypeScript errors or missing imports
- `backend/target/quarkus-app/` absent after `mvnw package` — build silently failed; check Maven output without `-q`
- `frontend/build/` absent after `bun run build` — Vite build failed; re-run without output suppression
- DEPLOY.md missing or empty — T03 did not complete; check task summary

## Not Proven By This UAT

- Live PostgreSQL connection from Railway service to Railway plugin (requires Railway infrastructure)
- HTTPS termination and custom domain routing (Railway-side configuration)
- Cold-start latency and health probe timing under Railway's probe schedule
- Actual `/q/health` HTTP response from a running Quarkus instance
- Mobile Safari accessibility of the deployed app
- Flyway migration execution against the Railway PostgreSQL instance on first startup
- Multi-service networking (SvelteKit → Quarkus via Railway private network)

## Notes for Tester

The backend Dockerfile builds from source via `./mvnw package -DskipTests` inside the container — Railway builds may take 3–5 minutes on first run due to Maven dependency download. Subsequent builds are faster if Railway caches the Maven local repo. The circular dependency warnings from `zod-v3-to-json-schema` in the frontend build are from a transitive dependency and do not affect runtime behavior.
