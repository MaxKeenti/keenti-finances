---
id: T01
parent: S08
milestone: M001
key_files:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/Dockerfile
key_decisions:
  - Used %prod profile prefix so dev config (localhost DB) remains the default and no env vars are needed for local development
  - Set %prod.quarkus.http.cors=false because Railway's private network topology means the SvelteKit frontend proxies to the backend — Quarkus never receives cross-origin browser requests directly
  - Multi-stage Dockerfile builds from source (./mvnw package -DskipTests) so Railway only needs the repo — no pre-built artifact required
duration: 
verification_result: passed
completed_at: 2026-05-14T20:42:35.365Z
blocker_discovered: false
---

# T01: Added quarkus-smallrye-health, %prod DB env-var profile, and multi-stage Dockerfile to backend for Railway deployment

**Added quarkus-smallrye-health, %prod DB env-var profile, and multi-stage Dockerfile to backend for Railway deployment**

## What Happened

Three changes were made to prepare the Quarkus backend for Railway production deployment:

1. **pom.xml** — added `quarkus-smallrye-health` dependency (no version needed; managed by quarkus-bom). This enables `/q/health` liveness and readiness endpoints that Railway will use as health probes.

2. **application.properties** — appended a `%prod` profile block with `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` env-var substitutions for the JDBC URL, username, and password. Also set `%prod.quarkus.http.cors=false` because SvelteKit handles CORS as a proxy; Quarkus is only reachable internally on Railway's private network. Existing dev properties are untouched.

3. **backend/Dockerfile** — created a multi-stage build: stage 1 uses `ubi9/openjdk-21` to run `./mvnw package -DskipTests`, stage 2 copies the `quarkus-app/` directory into `ubi9/openjdk-21-runtime` and launches via the standard `run-java.sh` entrypoint. Port 8080 exposed.

## Verification

Ran `./mvnw compile -q` in backend/ — exit 0, confirming the health extension resolves and compiles. Ran grep checks for `smallrye-health` in pom.xml, `DATABASE_URL` in application.properties, and existence of backend/Dockerfile — all passed.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw compile -q` | 0 | pass | 8200ms |
| 2 | `grep -q 'smallrye-health' backend/pom.xml && echo PASS` | 0 | pass | 30ms |
| 3 | `grep -q 'DATABASE_URL' backend/src/main/resources/application.properties && echo PASS` | 0 | pass | 20ms |
| 4 | `test -f backend/Dockerfile && echo PASS` | 0 | pass | 10ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/Dockerfile`
