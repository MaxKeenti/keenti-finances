---
estimated_steps: 5
estimated_files: 6
skills_used: []
---

# T03: Add transaction-subscription linking endpoint

**Slice:** S03 — Subscription Model Improvements
**Milestone:** M002

## Description

Users migrating from other services need to retroactively associate historical transactions with subscriptions. Atomic single-row update via PUT endpoint follows existing TransactionResource pattern. Also adds a query endpoint for linked transactions.

## Steps

1. Add PUT /api/transactions/{id}/link-subscription endpoint to TransactionResource. Request body: {subscriptionId: Long} (null to unlink). Returns updated TransactionResponse.
2. Add linkSubscription(Long transactionId, Long subscriptionId) method to TransactionService/TransactionUseCase.
3. In PanacheTransactionRepository: implement update of subscription_id column.
4. Add GET /api/subscriptions/{subId}/linked-transactions endpoint to SubscriptionResource that returns transactions where subscription_id = subId.
5. Validate that subscriptionId references an existing subscription before linking.

## Must-Haves

- [ ] PUT /api/transactions/{id}/link-subscription endpoint exists
- [ ] GET /api/subscriptions/{subId}/linked-transactions endpoint exists
- [ ] Subscription existence validated before linking
- [ ] ./mvnw compile -DskipTests succeeds

## Verification

- `./mvnw compile -DskipTests -f backend/pom.xml` succeeds
- Link and linked-transactions endpoints both exist in the resource classes

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java` — existing resource to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java` — existing resource to extend
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java` — existing service to extend
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` — T01 output with subscriptionId field
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java` — T01 output with subscriptionId mapper

## Expected Output

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java` — link-subscription PUT endpoint added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java` — linked-transactions GET endpoint added
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java` — linkSubscription method added
- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java` — linkSubscription added to interface
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java` — subscription_id update method added
- `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java` — findBySubscriptionId and updateSubscriptionId added
