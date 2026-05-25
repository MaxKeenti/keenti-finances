---
id: T03
parent: S01
milestone: M003
key_files:
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java
  - backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java
key_decisions:
  - Hibernate 6 @ParamDef requires type = Long.class (Class) not type = "long" (String) — fixed across all 5 entities
  - sumByDebtId scoped via JOIN to debt table rather than a subquery for clarity and index friendliness
  - Tests updated to pass X-WorkOS-User-Id header; a dedicated 401-without-header test added to verify filter boundary
duration: 
verification_result: passed
completed_at: 2026-05-23T18:42:23.786Z
blocker_discovered: false
---

# T03: User-scoped writes and native SQL queries enforced across all 5 repositories; @ParamDef Hibernate 6 type fix applied to all entities; tests updated to pass X-WorkOS-User-Id header.

**User-scoped writes and native SQL queries enforced across all 5 repositories; @ParamDef Hibernate 6 type fix applied to all entities; tests updated to pass X-WorkOS-User-Id header.**

## What Happened

Resumed after prior session had already written all 6 repository files with UserContext injection and user FK assignment on writes. Three remaining gaps were found and fixed:

1. **PanacheDebtPaymentRepository.sumByDebtId()** — the native SQL query only filtered by `debt_id` with no user scoping. Added an `INNER JOIN debt d ON dp.debt_id = d.id` and `AND d.user_id = :userId` condition, preventing cross-user payment sum leakage.

2. **@ParamDef type annotation** — all 5 entity files (CategoryEntity, ContactEntity, TransactionEntity, SubscriptionEntity, DebtEntity) had `@ParamDef(name = "userId", type = "long")` which Hibernate 6 rejects (expects `Class<?>` not `String`). Fixed to `type = Long.class` across all entities — this was a compile blocker from T02's work.

3. **CategoryResourceTest** — existing tests now receive 401 because UserScopeFilter is active and requires the X-WorkOS-User-Id header. Updated all 12 existing test cases to pass `header("X-WorkOS-User-Id", TEST_USER)`, and added a 13th test verifying that missing header returns 401.

Final state: all repositories compile, all native queries are user-scoped, all 13 tests pass including the 401 boundary case.

## Verification

Ran `./mvnw compile` — clean build. Ran `./mvnw test` — 13 tests pass, 0 failures. Filter log output confirmed auth.workos.scope.enabled and auth.workos.header.missing paths both exercised.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `./mvnw compile -q` | 0 | pass | 8000ms |
| 2 | `./mvnw test` | 0 | pass — 13 tests, 0 failures | 25000ms |

## Deviations

@ParamDef Hibernate 6 type fix (type = Long.class) was not in the T03 plan but was a compile-blocking consequence of T02's entity writes. Fixed here as it blocked all compilation.

## Known Issues

None.

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`
- `backend/src/test/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResourceTest.java`
