---
id: S01
parent: M003
milestone: M003
provides:
  - user_id FK (NOT NULL) on category, contact, transaction, subscription, debt tables
  - Hibernate @FilterDef/userScope filter on all 5 data entities
  - UserContext @RequestScoped CDI bean — available to all downstream slices
  - UserScopeFilter JAX-RS ContainerRequestFilter — activates userScope per request, 401 on missing header
  - app_user.workos_id column (unique, indexed) with JIT provisioning
  - Flyway V10 migration — existing rows backfilled to user 1
  - SvelteKit handleFetch hook — X-WorkOS-User-Id header injected on all backend requests when session exists
requires:
  []
affects:
  []
key_files:
  - backend/src/main/resources/db/migration/V10__multi_user_columns.sql
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java
  - frontend/src/hooks.server.ts
key_decisions:
  - Hibernate 6 @ParamDef requires type=Long.class (Class literal) not type="long" (String) — Hibernate 5 string form causes compile failure on all annotated entities
  - handleFetch clones the Request with a new Headers object rather than mutating — Request is immutable once constructed in the Fetch API
  - sumByDebtId native SQL scoped via JOIN to debt table (not a subquery) for index-friendliness and clarity
  - UserScopeFilter returns HTTP 401 (not 403) on missing header — header absence signals unauthenticated, not unauthorized
  - JIT provisioning inserts on first-seen workos_id with unique constraint as the race guard (no pessimistic lock needed)
patterns_established:
  - Hibernate @FilterDef + @Filter stacking pattern — S02 adds softDelete filter alongside userScope using identical annotation structure
  - UserContext CDI bean as the canonical user-resolution surface — all subsequent slices inject UserContext rather than reading headers directly
  - JAX-RS ContainerRequestFilter as the auth boundary — single choke point for header validation and filter activation
observability_surfaces:
  - auth.workos.scope.enabled — logged per authenticated request with path and userId
  - auth.workos.jit_provisioned — logged on first-seen WorkOS ID with new userId
  - auth.workos.header.missing — logged (WARN) on requests missing the header on non-public paths
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-23T18:49:06.706Z
blocker_discovered: false
---

# S01: Multi-user data foundation

**Added Flyway V10 migration with user_id FK on all 5 data tables, Hibernate @Filter row-level scoping on all entities, UserContext CDI bean with JIT provisioning, and SvelteKit handleFetch header injection — full multi-user data isolation foundation in place.**

## What Happened

Four tasks built the complete multi-user data isolation foundation for Keenti Finances.

**T01 — Flyway V10 migration and UserEntity workos_id:** Created `V10__multi_user_columns.sql` adding `user_id` FK (NOT NULL) to all 5 data tables (category, contact, transaction, subscription, debt) and backfilling existing rows to user 1. `UserEntity` gained a `workos_id` column (unique, indexed) enabling JIT provisioning of new WorkOS users into the local `app_user` table.

**T02 — UserContext, ContainerRequestFilter, and entity @Filter annotations:** Implemented the request-scoping stack: a `@RequestScoped` `UserContext` CDI bean holds the resolved local user ID for the request lifetime; `UserScopeFilter` (JAX-RS `ContainerRequestFilter`) reads the `X-WorkOS-User-Id` header, JIT-provisions unknown WorkOS IDs, activates the Hibernate `userScope` filter on the `EntityManager`, and returns HTTP 401 when the header is missing on non-public paths. All 5 data entities (`CategoryEntity`, `ContactEntity`, `TransactionEntity`, `SubscriptionEntity`, `DebtEntity`) received `@FilterDef` + `@Filter(name="userScope")` annotations.

**T03 — Repository write-scoping and native SQL query patching:** Completed write-path user assignment (all repository persist/merge paths explicitly set the user FK) and plugged the three native SQL query gaps that bypass Hibernate filters. A critical compile blocker discovered here: Hibernate 6 requires `@ParamDef(type = Long.class)` (a `Class` literal) rather than the Hibernate 5 string form `type = "long"` — this affected all 5 entities and was fixed here as it blocked all downstream compilation. Tests were updated to pass `X-WorkOS-User-Id`, and a dedicated 401 boundary test was added. Fresh `./mvnw test` run confirmed: 13/13 pass.

**T04 — SvelteKit handleFetch header injection:** Wired the frontend side via `handleFetch` in `hooks.server.ts`. The hook clones every backend-bound request with an additional `X-WorkOS-User-Id` header populated from the WorkOS session, implementing request immutability correctly (new `Headers` object, not mutation). Session-null guard ensures public pages (e.g., subscription landing) fetch the backend without the header as required.

## Verification

- `./mvnw test` (backend): **13/13 pass, 0 failures** — fresh run confirmed. Logs show `auth.workos.scope.enabled` on authenticated paths and `auth.workos.header.missing` on the 401 boundary test.
- `npm run build` (frontend): exits 0 with no type errors. Circular dependency warnings are pre-existing from third-party packages (zod-v3-to-json-schema, @internationalized/date) and are unrelated to this slice.
- Structural checks: `V10__multi_user_columns.sql` present; `UserScopeFilter.java` and `UserContext.java` present; all 5 data entity files carry `@FilterDef`/`@Filter`; `hooks.server.ts` injects `X-WorkOS-User-Id` header.

## Requirements Advanced

- R015 — Constraint reversed and implemented: multi-user data isolation via user_id FK on all tables replaces the prior single-user constraint
- R016 — user_id FK on all 5 data tables + Hibernate @Filter(userScope) enforcement at repository layer — all reads auto-scoped
- R017 — SvelteKit handleFetch injects X-WorkOS-User-Id; UserScopeFilter resolves WorkOS ID to local app_user.id; JIT provisioning on first login
- R018 — app_user.workos_id column added (unique, indexed); JIT provisioning links WorkOS string ID to numeric PK for all FK references
- R021 — V10 migration adds user_id and (column foundation for) deleted_at columns; existing rows backfilled to user 1 with no data loss
- R026 — user_id FK on category table with userScope filter enables per-user category ownership; default category seeding to follow in S05
- R027 — user_id FK on contact table with userScope filter — strict per-user contact ownership, no cross-user sharing possible

## Requirements Validated

None.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

["@ParamDef Hibernate 6 type fix (type=Long.class) discovered and applied in T03 — not in original T02/T03 plan but was a compile-blocking consequence of T02 entity annotations", "T01 and T02 verification deferred at time of execution (subagent unavailable); fresh ./mvnw test run in S01 closeout confirms all 13 tests pass"]

## Known Limitations

Runtime two-user isolation verification (curl/browser with two distinct WorkOS sessions) requires a running dev server and live WorkOS tenant — proof level is contract (compilation + test suite) per slice plan.

## Follow-ups

["S02 can now stack softDelete Hibernate filter alongside userScope — use identical @FilterDef/@Filter pattern on all entities", "S03-S05 should inject UserContext CDI bean for all user-scoped operations"]

## Files Created/Modified

None.
