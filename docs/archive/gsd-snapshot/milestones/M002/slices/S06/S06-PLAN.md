# S06: Deferred Fixes & Backend Tests

**Goal:** JUnit integration tests pass for CategoryResource and CategoryService; layerchart removed as unused; Fraunces font verified rendering correctly
**Demo:** JUnit integration tests pass for new backend logic; layerchart Svelte 5 status resolved; Fraunces font renders correctly

## Must-Haves

- 1. `./mvnw test -f backend/pom.xml` passes with CategoryResourceTest and CategoryServiceTest green\n2. layerchart removed from frontend/package.json; `cd frontend && npx vite build` exits 0\n3. Fraunces font renders on heading elements (visual verification via dev server); `cd frontend && npx vite build` clean

## Proof Level

- This slice proves: integration — tests exercise real Quarkus REST endpoints and service layer against H2 in-memory DB; font and dependency changes verified via build

## Integration Closure

Upstream surfaces consumed: Category color field from S02 (V7 migration, CategoryRequest/CategoryResponse with color); CategoryResource and CategoryService from existing backend.\nNew wiring introduced: H2 test database profile; JUnit test infrastructure.\nWhat remains before milestone is truly usable end-to-end: S07 Railway deployment and production verification.

## Verification

- Run the task and slice verification checks for this slice.

## Tasks

- [x] **T01: Set up backend test infrastructure and write CategoryResource integration tests** `est:1h`
  Why: The backend has zero test coverage. CategoryResource exposes CRUD endpoints including the color field added in S02. Integration tests need a working test profile with an in-memory database before any test can run.
  - Files: `backend/pom.xml`, `backend/src/test/resources/application.properties`, `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java`
  - Verify: ./mvnw test -f backend/pom.xml

- [x] **T02: Remove unused layerchart dependency and verify Fraunces font rendering** `est:30m`
  Why: layerchart has zero imports in frontend/src/ — it is dead weight that creates a false Svelte 5 compatibility concern (R012). Fraunces font (R013) is configured in layout.css @theme inline block as --font-heading and used in 5 UI components via the font-heading Tailwind utility. It needs visual verification and a fix if not rendering.
  - Files: `frontend/package.json`, `frontend/bun.lock`
  - Verify: cd frontend && npx vite build

## Files Likely Touched

- backend/pom.xml
- backend/src/test/resources/application.properties
- backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java
- frontend/package.json
- frontend/bun.lock
