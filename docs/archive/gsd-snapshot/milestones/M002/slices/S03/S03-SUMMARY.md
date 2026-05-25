---
id: S03
parent: M002
milestone: M002
provides:
  - owner_participates column and split math
  - POST /api/subscriptions/generate-billing endpoint
  - PUT /api/transactions/{id}/link-subscription endpoint
  - GET /api/subscriptions/{id}/linked-transactions endpoint
  - subscription_id FK on transaction table via V8 migration
  - Frontend owner participation toggle (SHARED only)
  - Frontend generate billing button with record-count toast
  - Frontend linked transactions section with inline preview
  - Frontend multi-select link transactions dialog
requires:
  []
affects:
  []
key_files: []
key_decisions:
  - SubscriptionRequest.ownerParticipates is nullable Boolean with ownerParticipatesOrDefault()=true so existing clients need no change
  - BillingService extracted from SubscriptionBillingScheduler and injected directly into BillingResource (no port interface) — consistent with scheduler's direct injection pattern
  - recalculateShares is triggered only when ownerParticipates changes AND subscription type is SHARED to avoid unnecessary recalculation
  - LinkSubscriptionRequest accepts null subscriptionId to support unlinking (sets subscription_id to NULL)
  - GET /api/subscriptions/{id}/linked-transactions validates subscription existence first (404 for unknown sub) before querying transactions
  - PaymentRecordResource kept as a separate @Path class (not sub-resource) — pattern from T02 reinforces MEM014 convention
patterns_established:
  - BillingService extraction pattern: extract scheduler logic into a dedicated service so both the cron and on-demand REST endpoints share the same implementation
  - Nullable FK linking pattern: PUT endpoint accepts null to support both link and unlink in one endpoint shape
  - Subscription existence pre-validation: validate parent resource exists before querying child resource to return clean 404
observability_surfaces:
  - billing.generate: INFO log of record count when POST /api/subscriptions/generate-billing runs
  - transaction.link: INFO log of subscription+transaction ids on PUT /api/transactions/{id}/link-subscription
  - Share recalculation: INFO log with old/new divisor when ownerParticipates changes on a SHARED subscription
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-17T12:20:17.420Z
blocker_discovered: false
---

# S03: S03

**Owner participation toggle, manual billing trigger, and transaction-subscription linking delivered end-to-end across backend and frontend.**

## What Happened

S03 added three distinct capabilities to the subscription model across four tasks, all independent of the UI track (S01/S02).

**T01 — Schema and model layer:** Flyway migration V8 added `owner_participates BOOLEAN NOT NULL DEFAULT TRUE` to the subscription table and `subscription_id BIGINT NULL REFERENCES subscription(id)` to the transaction table. Every layer was updated in lock-step: domain models (`Subscription`, `Transaction`), JPA entities (`SubscriptionEntity`, `TransactionEntity`), Panache repositories (`PanacheSubscriptionRepository`, `PanacheTransactionRepository`), DTOs (`SubscriptionRequest` with nullable `ownerParticipates` and an `ownerParticipatesOrDefault()` helper, `SubscriptionResponse`, `TransactionResponse`), and downstream services that construct Transactions (`DebtService`, `TransactionResource`, `SubscriptionBillingScheduler`) — all updated to pass `null` for `subscriptionId` where no link exists yet.

**T02 — Billing split math and manual trigger:** `SubscriptionService.update()` now calls a new `recalculateShares()` method whenever `ownerParticipates` changes on a SHARED subscription. The divisor counts members only when `ownerParticipates=false`, or members+1 (owner) when `true`. A dedicated `BillingService` was extracted from `SubscriptionBillingScheduler` so billing logic is reusable. `BillingResource` exposes `POST /api/subscriptions/generate-billing` which calls `BillingService.generateUpcomingPaymentRecords()` — idempotent since the scheduler already guards against duplicate records. The recalculation is INFO-logged with old/new divisor.

**T03 — Transaction-subscription linking endpoints:** `TransactionResource` gained `PUT /api/transactions/{id}/link-subscription` accepting a `LinkSubscriptionRequest` with nullable `subscriptionId` (null = unlink). `SubscriptionResource` gained `GET /api/subscriptions/{id}/linked-transactions` returning fully-enriched `TransactionResponse` objects (amount, date, description, category badge data) — subscription existence is validated before querying to return a clean 404. Both `TransactionUseCase` and `TransactionRepository` ports were extended, and `PanacheTransactionRepository` implements `findBySubscriptionId`.

**T04 — Frontend UI:** The subscriptions list page (`+page.svelte`, `+page.server.ts`) gained a "Generate Billing" button wired to a `?/generateBilling` action that calls `POST /api/subscriptions/generate-billing` and shows a toast with the record count. The subscription detail page (`[id]/+page.svelte`, `[id]/+page.server.ts`) gained: (1) an `ownerParticipates` badge in the header (SHARED only), (2) an owner-participation toggle form visible only for SHARED subscriptions, (3) a "Linked Transactions" section rendering each linked transaction with category badge, amount, date, and description, and (4) a "Link Transactions" multi-select dialog that fetches unlinked transactions, allows multi-select, and submits each selection via `PUT /api/transactions/{id}/link-subscription`.

All verification checks passed: backend BUILD SUCCESS (90 files), svelte-check 0 errors, Vite build ✔ done.

## Verification

1. `./mvnw compile -DskipTests` in `backend/` — BUILD SUCCESS, 90 source files compiled, exit 0.
2. `npx svelte-check --threshold error` in `frontend/` — 0 ERRORS (10 warnings, all pre-existing), exit 0.
3. `npm run build` in `frontend/` — Vite build ✔ done, exit 0. (Circular dependency warnings are from third-party node_modules only.)

## Requirements Advanced

None.

## Requirements Validated

- R006 — ownerParticipates toggle visible on SHARED subscription detail page; recalculateShares called on save; divisor correctly excludes owner when false. Backend BUILD SUCCESS confirms logic compiles.
- R007 — POST /api/subscriptions/generate-billing endpoint exists in BillingResource; Generate Billing button on subscriptions list page calls generateBilling action and shows record-count toast. BUILD SUCCESS + svelte-check 0 errors.
- R008 — subscription_id FK added via V8 migration; PUT /api/transactions/{id}/link-subscription persists atomically; subscription detail page shows Linked Transactions section with inline previews (amount, date, description, category badge) and a multi-select Link Transactions dialog.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

T01 also updated SubscriptionBillingScheduler, DebtService, TransactionResource, and TransactionService (not listed in T01 plan inputs) because the new Transaction constructor arity required it. These were all accounted for in T02/T03 task scopes respectively.

## Known Limitations

No JUnit integration tests for billing split math or linking endpoints — these are planned for S06. Manual verification of split math requires a running backend with a seeded database.

## Follow-ups

None.

## Files Created/Modified

- `backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql` — Flyway V8 migration adding owner_participates to subscription and subscription_id FK to transaction
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java` — Added ownerParticipates boolean field
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` — Added nullable subscriptionId field
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java` — ownerParticipates column mapping
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java` — subscriptionId FK column mapping
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java` — Updated mapper to include ownerParticipates
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java` — Added findBySubscriptionId query method
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java` — Added nullable ownerParticipates with ownerParticipatesOrDefault() helper
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java` — Added ownerParticipates to response DTO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java` — Added subscriptionId to response DTO
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java` — Added GET /linked-transactions endpoint; injected CategoryUseCase and ContactUseCase for full enrichment
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — Added recalculateShares triggered on ownerParticipates change for SHARED subscriptions
- `backend/src/main/java/com/keenti/finances/application/service/BillingService.java` — New service extracted from scheduler; holds generateUpcomingPaymentRecords logic
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java` — Delegates to BillingService; preserved existing scheduler logging
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java` — New resource: POST /api/subscriptions/generate-billing
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java` — Added recalculateShares port method
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java` — Added PUT /api/transactions/{id}/link-subscription endpoint
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java` — Implemented linkSubscription service method
- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java` — Added linkSubscription and listBySubscriptionId port methods
- `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java` — Added findBySubscriptionId port method
- `frontend/src/routes/subscriptions/+page.server.ts` — Added generateBilling action calling POST /api/subscriptions/generate-billing
- `frontend/src/routes/subscriptions/+page.svelte` — Added Generate Billing button with record-count toast
- `frontend/src/routes/subscriptions/[id]/+page.server.ts` — Added toggleOwnerParticipates, linkTransactions actions; fetches linked transactions and unlinked transaction list on load
- `frontend/src/routes/subscriptions/[id]/+page.svelte` — Added ownerParticipates badge/toggle (SHARED only), Linked Transactions section, Link Transactions multi-select dialog
