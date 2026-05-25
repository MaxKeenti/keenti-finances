---
id: T01
parent: S06
milestone: M002
key_files:
  - backend/pom.xml
  - backend/src/test/resources/application.properties
  - backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java
key_decisions:
  - Disabled Flyway in test profile and used Hibernate drop-and-create — H2 PostgreSQL MODE cannot run all Postgres-specific SQL in migration scripts
  - Disabled scheduler in test profile to prevent background jobs from interfering with integration tests
  - Used @TestMethodOrder(OrderAnnotation.class) to keep CRUD tests sequential against shared in-memory DB state without resetting between tests
duration: 
verification_result: passed
completed_at: 2026-05-17T23:45:54.804Z
blocker_discovered: false
---

# T01: Added H2 test infra and 12-test CategoryResourceTest covering CRUD, color field, validation, and error paths — all green in 8s

**Added H2 test infra and 12-test CategoryResourceTest covering CRUD, color field, validation, and error paths — all green in 8s**

## What Happened

The backend had zero test coverage. Three changes were made: (1) pom.xml: replaced `quarkus-junit` with `quarkus-junit5` (the correct artifact for Quarkus 3.x; Maven showed a relocation warning confirming the old name is an alias pointing to the new one) and added `quarkus-jdbc-h2` test-scope. (2) Created `backend/src/test/resources/application.properties` configuring H2 in-memory DB with PostgreSQL compatibility mode, Hibernate DDL generation (drop-and-create), Flyway disabled (H2 cannot run the Postgres-specific migration SQL), and the scheduler disabled to avoid background noise in tests. (3) Created `CategoryResourceTest.java` with 12 ordered tests using `@QuarkusTest` + REST Assured, covering: happy-path CRUD with color field assertions, GET-after-delete (404), invalid type (400), duplicate name (409), nonexistent ID GET/PUT (404), empty name (400), and color exceeding max length (400). All 12 tests passed on first run in ~8s total build time.

## Verification

Ran `./mvnw test` in backend/. Build succeeded: Tests run: 12, Failures: 0, Errors: 0, Skipped: 0. Also confirmed test file and test application.properties exist at expected paths.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw test` | 0 | pass | 8319ms |

## Deviations

quarkus-junit5 shows a Maven relocation warning ('has been relocated to quarkus-junit') — this is expected; both names resolve to the same jar in Quarkus 3.35.2. No functional impact.

## Known Issues

none

## Files Created/Modified

- `backend/pom.xml`
- `backend/src/test/resources/application.properties`
- `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java`
