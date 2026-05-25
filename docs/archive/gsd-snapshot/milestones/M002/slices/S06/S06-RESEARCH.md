# S06 Research: Deferred Fixes & Backend Tests

## Summary of Findings

### 1. Backend Tests (R010)

**Current state:** No test files exist. The `backend/src/test/` directory is completely empty. The project has never had any tests.

**Test framework configured:**
- `quarkus-junit` dependency in pom.xml (note: the correct artifact for Quarkus 3.x is `quarkus-junit5` — this may need correction)
- `rest-assured` dependency already present for REST endpoint testing
- Surefire plugin configured with JBoss LogManager
- Failsafe plugin configured for integration tests (skipped by default via `<skipITs>true</skipITs>`)

**Architecture (hexagonal/ports-and-adapters):**
- Domain models: Category, Subscription, SubscriptionMember, PaymentRecord, Transaction, etc.
- Ports (in): CategoryUseCase, SubscriptionUseCase, PaymentRecordUseCase, etc.
- Ports (out): CategoryRepository, SubscriptionRepository, PaymentRecordRepository, etc.
- Services: CategoryService, SubscriptionBillingScheduler, SubscriptionService, etc.
- REST adapters: CategoryResource, SubscriptionResource, PaymentRecordResource, etc.

**What needs test coverage now (from S02):**
- `CategoryResource` — CRUD with color field (GET/POST/PUT/DELETE)
- `CategoryService` — validation logic (valid types, duplicate name detection, color persistence)

**What S03 will add (defer until S03 completes):**
- `SubscriptionBillingScheduler` — billing split with owner_participates toggle
- Manual billing endpoint (POST /api/subscriptions/generate-billing)
- Transaction linking endpoint (PUT /api/transactions/{id}/link-subscription)

**Test infrastructure needed:**
- Create `backend/src/test/java/...` directory structure
- Create `backend/src/test/resources/application.properties` with test DB config (H2 or Testcontainers)
- Fix dependency: `quarkus-junit` → `quarkus-junit5` (or verify it resolves correctly in Quarkus 3.35)
- Consider adding `quarkus-test-h2` or `quarkus-jdbc-h2` for test scope

### 2. Layerchart (R012)

**Current state:**
- Dependency in `frontend/package.json`: `"layerchart": "^1.0.13"`
- Listed in `bun.lock`
- **NOT imported or used anywhere** in `frontend/src/` — zero imports found

**Assessment:** Layerchart is an unused dependency. It was likely added in anticipation of charting features but never integrated. The Svelte 5 compatibility concern is moot since it is not actively used. This becomes a documentation-only outcome: confirm it is unused, document decision (keep for future use or remove), and close.

### 3. Fraunces Font (R013)

**Current state:**
- Package installed: `@fontsource-variable/fraunces: ^5.2.9`
- Imported in `frontend/src/routes/layout.css` via `@import "@fontsource-variable/fraunces"`
- CSS variable defined: `--font-heading: 'Fraunces Variable', serif`
- Used via `font-heading` Tailwind utility in: card-title, dialog-title, alert-title, empty-title, popover-title components

**Likely "fix" needed:** The font appears to be configured correctly at the CSS level. Possible issues:
- Font may not be rendering due to a Tailwind v4 `@theme` configuration issue (the `--font-heading` is defined inside `@theme inline {}` block — need to verify Tailwind picks it up as a font-family utility)
- Could be a build/bundling issue where fontsource variable fonts aren't resolved
- Visual verification needed: does `font-heading` actually produce Fraunces glyphs in the browser?

**Files involved:**
- `frontend/src/routes/layout.css` (lines 5, 80)
- `frontend/package.json` (dependency)
- Components using `font-heading` class

## Implementation Landscape

| Sub-item | Files to Create/Modify |
|----------|----------------------|
| Backend tests | `backend/pom.xml` (fix test dep), `backend/src/test/resources/application.properties` (new), `backend/src/test/java/.../CategoryResourceTest.java` (new), `backend/src/test/java/.../CategoryServiceTest.java` (new) |
| Layerchart | Documentation/decision only — possibly `frontend/package.json` if removing |
| Fraunces font | `frontend/src/routes/layout.css`, possibly Tailwind config |

## Natural Task Seams

1. **T01: Backend integration tests for CategoryResource + CategoryService** — Create test infrastructure (test application.properties, fix pom.xml dep if needed), write REST-assured tests for category CRUD including color field. Independent of S03.

2. **T02: Layerchart Svelte 5 resolution** — Verify unused status, make keep/remove decision, document outcome. Minimal code change (possibly remove from package.json).

3. **T03: Fraunces font fix** — Verify rendering, diagnose if broken, fix CSS/config. Likely a Tailwind v4 theme integration issue.

## Verification Approach

- **T01:** `./mvnw test` passes with new tests green; tests cover Category CRUD (create with color, update color, list returns color, validation errors)
- **T02:** `bun run build` still succeeds after any package.json changes; documented decision
- **T03:** Visual check that heading elements render in Fraunces; `bun run build` clean

## Dependencies on S03

S03 will produce:
- `owner_participates` boolean on subscriptions — changes billing split logic in `SubscriptionBillingScheduler`
- Manual billing endpoint (POST /api/subscriptions/generate-billing)
- `subscription_id` FK on transactions table

**What to defer:** Tests for SubscriptionBillingScheduler's updated split logic and the new endpoints should be written after S03 lands. The test infrastructure and Category tests can proceed now.

**What to do now:** All three tasks (T01 Category tests, T02 Layerchart, T03 Fraunces) are independent of S03 and can proceed immediately.

## Risks & Constraints

- **Low risk:** The `quarkus-junit` artifact name may resolve correctly in Quarkus 3.35 (it may be an alias). If not, switching to `quarkus-junit5` is trivial.
- **Test DB:** No test database profile exists. Need to decide between H2 in-memory (simpler, may have Postgres syntax gaps) or Testcontainers (heavier but accurate). For a personal app, H2 with Postgres compatibility mode is pragmatic.
- **Fraunces:** If the font is actually rendering correctly and R013 was filed prematurely, this becomes a verification-only task.
- **Layerchart:** If the team wants charts in a future milestone, removing the dependency now means re-adding later. Low stakes either way.
