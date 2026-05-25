# S03: Subscription Model Improvements

**Goal:** Owner participation toggle changes billing split math; manual trigger generates payment records on demand; transactions linkable to subscriptions with inline preview on subscription detail page.
**Demo:** Owner participation toggle changes billing split math; manual trigger generates records on demand; transactions linkable to subscriptions with inline preview

## Must-Haves

- 1. SHARED subscription with ownerParticipates=false splits cost among members only (not owner). 2. Toggling ownerParticipates recalculates all member shares. 3. POST /api/subscriptions/generate-billing creates payment records for eligible subscriptions idempotently. 4. PUT /api/transactions/{id}/link-subscription persists FK atomically. 5. Subscription detail page shows linked transactions with inline previews. 6. ./mvnw compile succeeds, frontend builds clean.

## Proof Level

- This slice proves: integration — real database via Flyway migration, backend compilation, frontend build, manual verification of split math and endpoint behavior

## Integration Closure

Upstream: none (independent backend track). New wiring: V8 migration adds columns, new REST endpoints exposed, frontend forms and actions call new endpoints. Remains before milestone usable: S04 mobile cards, S05 passkey auth, S06 tests, S07 deploy.

## Verification

- Structured logs added: billing.generate (count of records created), transaction.link (subscription+transaction ids). Existing scheduler logging preserved. Share recalculation logged at INFO with old/new divisor.

## Tasks

- [x] **T01: Flyway V8 migration and backend model layer changes for ownerParticipates and subscriptionId** `est:1h`
  Why: All subsequent backend work depends on the database schema having owner_participates on subscription and subscription_id FK on transaction, plus the domain/entity/DTO layers reflecting these fields.
  - Files: `backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql`, `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`, `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
  - Verify: ./mvnw compile -DskipTests -f backend/pom.xml

- [x] **T02: Fix billing split math for owner participation and add manual billing endpoint** `est:1h`
  Why: The core business logic change — split divisor must account for ownerParticipates flag. Manual billing endpoint reuses scheduler logic so users don't wait for the 1am cron.
  - Files: `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java`, `backend/src/main/java/com/keenti/finances/application/service/BillingService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
  - Verify: ./mvnw compile -DskipTests -f backend/pom.xml

- [x] **T03: Add transaction-subscription linking endpoint** `est:45m`
  Why: Users migrating from other services need to retroactively associate historical transactions with subscriptions. Atomic single-row update via PUT endpoint follows existing TransactionResource pattern.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`, `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`
  - Verify: ./mvnw compile -DskipTests -f backend/pom.xml

- [x] **T04: Frontend owner participation toggle, generate billing button, and transaction linking UI** `est:2h`
  Why: Users need UI controls to toggle owner participation on SHARED subscriptions, trigger billing generation, and link transactions to subscriptions from the detail page.
  - Files: `frontend/src/routes/subscriptions/+page.server.ts`, `frontend/src/routes/subscriptions/+page.svelte`, `frontend/src/routes/subscriptions/[id]/+page.server.ts`, `frontend/src/routes/subscriptions/[id]/+page.svelte`
  - Verify: cd frontend && npx svelte-check --threshold error

## Files Likely Touched

- backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql
- backend/src/main/java/com/keenti/finances/domain/model/Subscription.java
- backend/src/main/java/com/keenti/finances/domain/model/Transaction.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java
- backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
- backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java
- backend/src/main/java/com/keenti/finances/application/service/BillingService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java
- backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java
- backend/src/main/java/com/keenti/finances/application/service/TransactionService.java
- backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java
- frontend/src/routes/subscriptions/+page.server.ts
- frontend/src/routes/subscriptions/+page.svelte
- frontend/src/routes/subscriptions/[id]/+page.server.ts
- frontend/src/routes/subscriptions/[id]/+page.svelte
