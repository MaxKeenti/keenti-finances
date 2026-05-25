# S01: Multi-user data foundation

**Goal:** Two WorkOS users log in; each creates a transaction and a category; each sees only their own data; existing admin data intact under user 1. Hibernate @Filter enforces row-level isolation; SvelteKit propagates user identity via X-WorkOS-User-Id header.
**Demo:** Two WorkOS users log in; each creates a transaction and a category; each sees only their own data; existing admin data intact under user 1

## Must-Haves

- All 5 data tables (category, contact, transaction, subscription, debt) have user_id FK with NOT NULL constraint
- Existing data migrated to user 1 via Flyway V10
- UserEntity has workos_id column with JIT provisioning
- Hibernate @Filter(userScope) on all direct-ownership entities auto-scopes reads
- UserContext @RequestScoped bean populated by ContainerRequestFilter
- All repository writes explicitly set user FK
- 3 native SQL queries include WHERE user_id clause
- SvelteKit handleFetch injects X-WorkOS-User-Id header on backend requests
- App compiles and starts without migration errors
- svelte-check and vite build pass

## Proof Level

- This slice proves: This slice proves: contract — compilation, migration, and type-check verify structural correctness. Integration verification (two users seeing isolated data) requires runtime testing with dev server.

## Integration Closure

Upstream surfaces consumed: none (first slice). New wiring: user_id FK on all data tables, Hibernate @Filter for query scoping, UserContext CDI bean, ContainerRequestFilter for user resolution, SvelteKit handleFetch for header injection. What remains: S02 stacks softDelete filter alongside userScope; S03-S05 consume UserContext and per-user category ownership.

## Verification

- Runtime signals: ContainerRequestFilter logs user resolution (JIT provisioning events, missing header on non-public paths)
- Inspection surfaces: Quarkus dev mode logs show filter enablement per request; curl with X-WorkOS-User-Id header verifies scoping
- Failure visibility: 401 response when header missing on non-public path; unique constraint violation logged on JIT race condition (retried internally)
- Redaction constraints: workos_id is not a secret but should not appear in user-facing error messages

## Tasks

- [x] **T01: Flyway V10 migration and UserEntity workos_id column** `est:1h`
  Why: All downstream tasks depend on user_id FK columns existing on data tables and workos_id on app_user. This migration backfills existing data to user 1 and establishes the multi-user schema.
  - Files: `backend/src/main/resources/db/migration/V10__multi_user_columns.sql`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java`, `backend/src/main/java/com/keenti/finances/domain/model/User.java`
  - Verify: ./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true

- [x] **T02: UserContext bean, ContainerRequestFilter, and entity @Filter annotations** `est:2h`
  Why: Core multi-user mechanism. Hibernate @Filter on entities + JAX-RS ContainerRequestFilter provides defense-in-depth row-level isolation. This is the first proof — if @Filter doesn't work with Panache static methods, we need to know immediately.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`
  - Verify: ./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true

- [x] **T03: Repository user-scoping for writes and native SQL queries** `est:1h30m`
  Why: Entity @Filters scope reads, but writes must explicitly set the user FK. Three native SQL queries bypass Hibernate filters and need manual WHERE user_id clauses — missing one is a data leak.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java`
  - Verify: ./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true

- [x] **T04: SvelteKit handleFetch hook for user identity header injection** `est:45m`
  Why: Backend now requires X-WorkOS-User-Id on all non-public requests. SvelteKit load/action functions make direct backend calls, so the header must be injected centrally via handleFetch hook.
  - Files: `frontend/src/hooks.server.ts`
  - Verify: cd frontend && npm run build

## Files Likely Touched

- backend/src/main/resources/db/migration/V10__multi_user_columns.sql
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java
- backend/src/main/java/com/keenti/finances/domain/model/User.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java
- frontend/src/hooks.server.ts
