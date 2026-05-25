---
id: S06
parent: M002
milestone: M002
provides:
  - (none)
requires:
  []
affects:
  []
key_files: []
key_decisions:
  - Disabled Flyway in test profile and used Hibernate drop-and-create — H2 PostgreSQL MODE cannot run all Postgres-specific migration SQL
  - Disabled scheduler in test profile to prevent background jobs from interfering with integration tests
  - Used @TestMethodOrder(OrderAnnotation.class) to keep CRUD tests sequential against shared in-memory DB state without resetting between tests
  - Fraunces font rendering was verification-only — @theme inline @font-heading convention is correct for Tailwind v4, no fix needed
patterns_established:
  - (none)
observability_surfaces:
  - none
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-18T00:07:11.803Z
blocker_discovered: false
---

# S06: Deferred Fixes & Backend Tests

**Added H2-backed integration tests (12 green) for CategoryResource, removed dead layerchart dep, and verified Fraunces Variable font renders correctly**

## What Happened

S06 addressed three deferred items. T01 established backend test infrastructure from scratch: pom.xml gained quarkus-junit5 and quarkus-jdbc-h2 (test scope); a test application.properties configured H2 in PostgreSQL compatibility mode with Hibernate DDL (drop-and-create), Flyway disabled (H2 can't execute Postgres-specific migration SQL), and the scheduler suppressed to avoid background noise. CategoryResourceTest.java covers 12 ordered cases — happy-path CRUD with color field assertions, GET-after-delete (404), invalid type (400), duplicate name (409), nonexistent ID (404), empty name (400), and color length overflow (400). All 12 passed on first run in ~8s.

T02 removed layerchart from frontend/package.json (zero imports confirmed via grep) and ran bun install, which dropped exactly 1 package and cleaned bun.lock. vite build exited clean. Fraunces font verification confirmed the @theme inline convention in layout.css is correct for Tailwind v4 — the built CSS contains .font-heading{font-family:Fraunces Variable,serif} and all woff2 assets are present in the build output. No fix was needed.

## Verification

T01: ./mvnw test — Tests run: 12, Failures: 0, Errors: 0, Skipped: 0 (exit 0, ~8s). T02: bun install exit 0 (1 package removed); npx vite build exit 0; grep confirms layerchart absent from package.json and bun.lock; built CSS contains correct .font-heading rule; Fraunces woff2 assets present in build output.

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

None.

## Known Limitations

None.

## Follow-ups

None.

## Files Created/Modified

- `backend/pom.xml` — Replaced quarkus-junit with quarkus-junit5; added quarkus-jdbc-h2 test-scope dependency
- `backend/src/test/resources/application.properties` — H2 in-memory test DB config — PostgreSQL compat mode, Hibernate DDL, Flyway disabled, scheduler disabled
- `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java` — 12 ordered integration tests covering CategoryResource CRUD, color field, validation, and error paths
- `frontend/package.json` — Removed layerchart dependency
- `frontend/bun.lock` — Updated after bun install removed layerchart
