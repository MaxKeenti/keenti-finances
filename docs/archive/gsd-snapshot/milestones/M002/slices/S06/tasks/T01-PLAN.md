---
estimated_steps: 8
estimated_files: 3
skills_used:
  - tdd
---

# T01: Set up backend test infrastructure and write CategoryResource integration tests

**Slice:** S06 — Deferred Fixes & Backend Tests
**Milestone:** M002

## Description

The backend has zero test coverage. CategoryResource exposes CRUD endpoints including the color field added in S02. Integration tests need a working test profile with an in-memory database before any test can run.

## Steps

1. Fix pom.xml: change `quarkus-junit` to `quarkus-junit5` (correct artifact for Quarkus 3.x). Add `quarkus-jdbc-h2` with test scope for in-memory test DB.
2. Create `backend/src/test/resources/application.properties` with H2 config: `quarkus.datasource.db-kind=h2`, `quarkus.datasource.jdbc.url=jdbc:h2:mem:test;MODE=PostgreSQL`, `quarkus.hibernate-orm.database.generation=drop-and-create`, `quarkus.flyway.migrate-at-start=false` (use Hibernate DDL for tests since H2 may not support all Flyway Postgres syntax).
3. Create `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java` using `@QuarkusTest` + REST Assured:
   - POST /api/categories with valid body (name, type INGRESS, color 145) → 201, response has id + color
   - GET /api/categories → 200, list includes created category with color
   - GET /api/categories/{id} → 200 with correct fields
   - PUT /api/categories/{id} with updated color → 200, color changed
   - DELETE /api/categories/{id} → 204
   - POST with invalid type → 400
   - POST with duplicate name → 409
   - GET /api/categories/{nonexistent} → 404
4. Run `./mvnw test` and verify all tests pass.

## Must-Haves

- [ ] quarkus-junit5 artifact in pom.xml (not quarkus-junit)
- [ ] quarkus-jdbc-h2 test-scope dependency added
- [ ] Test application.properties with H2 in PostgreSQL compatibility mode
- [ ] CategoryResourceTest with 8+ test methods covering CRUD + validation + color

## Negative Tests

- **Malformed inputs**: POST with empty name, POST with null type, POST with color exceeding 10 chars
- **Error paths**: POST duplicate category name returns 409, GET nonexistent ID returns 404
- **Boundary conditions**: POST with invalid type returns 400, PUT to nonexistent returns 404

## Verification

- `./mvnw test -f backend/pom.xml`
- Test output shows CategoryResourceTest with 8+ tests passing

## Inputs

- `backend/pom.xml`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/resources/application.properties`

## Expected Output

- `backend/pom.xml`
- `backend/src/test/resources/application.properties`
- `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java`
