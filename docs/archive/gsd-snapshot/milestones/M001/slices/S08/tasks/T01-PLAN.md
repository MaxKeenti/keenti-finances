---
estimated_steps: 17
estimated_files: 3
skills_used: []
---

# T01: Configure Quarkus backend for production deployment

Add production profile to application.properties with env-var-based DB configuration, add the quarkus-smallrye-health extension for Railway health probes, and create a root-level Dockerfile in backend/ based on the existing Dockerfile.jvm template.

## Steps

1. Add `quarkus-smallrye-health` dependency to `backend/pom.xml`.
2. Add `%prod` profile properties to `application.properties`: `%prod.quarkus.datasource.jdbc.url=${DATABASE_URL}`, `%prod.quarkus.datasource.username=${DATABASE_USER}`, `%prod.quarkus.datasource.password=${DATABASE_PASSWORD}`. Keep existing dev properties as-is (they are the default profile).
3. Add `%prod.quarkus.http.cors=false` (SvelteKit proxy handles CORS, Quarkus is internal-only on Railway's private network).
4. Create `backend/Dockerfile` based on `src/main/docker/Dockerfile.jvm`: multi-stage build — stage 1 runs `./mvnw package -DskipTests`, stage 2 copies the quarkus-app directory into the UBI minimal runtime image. Expose port 8080.
5. Verify: `./mvnw compile -q` exits 0 (health extension compiles), `grep -q 'DATABASE_URL' application.properties`, Dockerfile exists.

## Must-Haves

- [ ] `%prod` profile properties use env vars for all DB config
- [ ] `quarkus-smallrye-health` in pom.xml
- [ ] Multi-stage Dockerfile that builds and runs the Quarkus app
- [ ] Existing dev properties unchanged

## Verification

- `./mvnw compile -q` exits 0
- `grep -q 'smallrye-health' backend/pom.xml`
- `grep -q 'DATABASE_URL' backend/src/main/resources/application.properties`
- `test -f backend/Dockerfile`

## Inputs

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/src/main/docker/Dockerfile.jvm`

## Expected Output

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
- `backend/Dockerfile`

## Verification

./mvnw compile -q && grep -q 'smallrye-health' backend/pom.xml && grep -q 'DATABASE_URL' backend/src/main/resources/application.properties && test -f backend/Dockerfile

## Observability Impact

Adds /q/health liveness and readiness endpoints via SmallRye Health for Railway health probes. Production DB connection failures surface through health check status.
