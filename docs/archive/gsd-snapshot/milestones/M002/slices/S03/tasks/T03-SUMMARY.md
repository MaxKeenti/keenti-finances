---
id: T03
parent: S03
milestone: M002
key_files:
  - backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java
  - backend/src/main/java/com/keenti/finances/application/service/TransactionService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LinkSubscriptionRequest.java
key_decisions:
  - LinkSubscriptionRequest accepts null subscriptionId to support unlinking (passing null sets subscription_id to NULL in DB via existing update() path)
  - SubscriptionResource injects CategoryUseCase and ContactUseCase to return fully-enriched TransactionResponse from linked-transactions endpoint, matching the shape TransactionResource returns
  - listBySubscriptionId validates subscription existence first (throws 404 for unknown sub) before querying transactions
duration: 
verification_result: passed
completed_at: 2026-05-17T07:09:51.857Z
blocker_discovered: false
---

# T03: Added PUT /api/transactions/{id}/link-subscription and GET /api/subscriptions/{id}/linked-transactions endpoints with subscription existence validation.

**Added PUT /api/transactions/{id}/link-subscription and GET /api/subscriptions/{id}/linked-transactions endpoints with subscription existence validation.**

## What Happened

Implemented the transaction-subscription linking endpoint across all layers. Added findBySubscriptionId to TransactionRepository interface and PanacheTransactionRepository (using Panache query on subscription.id). Added linkSubscription and listBySubscriptionId methods to TransactionUseCase interface and TransactionService. TransactionService injects SubscriptionRepository to validate subscription existence before linking (throws NotFoundException for unknown subscriptionId). Structured logging added at transaction.link with transactionId and subscriptionId. Created LinkSubscriptionRequest record (subscriptionId: Long, nullable to support unlinking). Added PUT /{id}/link-subscription to TransactionResource. Added GET /{id}/linked-transactions to SubscriptionResource with full TransactionResponse enrichment (category name/color, contact name) by injecting CategoryUseCase and ContactUseCase. Build succeeds cleanly.

## Verification

Ran ./mvnw compile -DskipTests -f backend/pom.xml — BUILD SUCCESS. Verified both endpoint methods exist in TransactionResource and SubscriptionResource respectively.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `/Users/moonstone/Source/Personal/keenti-finances/.gsd/worktrees/M002/backend/mvnw compile -DskipTests -f /Users/moonstone/Source/Personal/keenti-finances/.gsd/worktrees/M002/backend/pom.xml` | 0 | BUILD SUCCESS — 90 source files compiled | 1871ms |

## Deviations

None.

## Known Issues

None.

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LinkSubscriptionRequest.java`
