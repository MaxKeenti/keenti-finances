---
estimated_steps: 11
estimated_files: 12
skills_used: []
---

# T01: Flyway V8 migration and backend model layer changes for ownerParticipates and subscriptionId

**Slice:** S03 — Subscription Model Improvements
**Milestone:** M002

## Description

All subsequent backend work depends on the database schema having owner_participates on subscription and subscription_id FK on transaction, plus the domain/entity/DTO layers reflecting these fields. Create the Flyway V8 migration and update all model/entity/mapper/DTO layers.

## Steps

1. Create V8 migration: ALTER subscription ADD COLUMN owner_participates BOOLEAN NOT NULL DEFAULT true; ALTER transaction ADD COLUMN subscription_id BIGINT REFERENCES subscription(id).
2. Add ownerParticipates field to Subscription domain model (update constructor, add getter).
3. Add ownerParticipates column to SubscriptionEntity.
4. Update PanacheSubscriptionRepository mappers (toDomain/toEntity) to include ownerParticipates.
5. Add ownerParticipates to SubscriptionRequest (Boolean, default true if null) and SubscriptionResponse.
6. Add subscriptionId field to Transaction domain model (update constructor, add getter).
7. Add subscription_id ManyToOne to TransactionEntity.
8. Update PanacheTransactionRepository mappers to include subscriptionId.
9. Add subscriptionId to TransactionResponse record.
10. Update SubscriptionResource.toSubscription and toResponse to pass ownerParticipates.
11. Update SubscriptionService.create and update to pass ownerParticipates through constructors.

## Must-Haves

- [ ] V8 migration file exists with owner_participates and subscription_id columns
- [ ] Subscription domain model, entity, mapper, request, and response all include ownerParticipates
- [ ] Transaction domain model, entity, mapper, and response all include subscriptionId
- [ ] ./mvnw compile -DskipTests succeeds

## Verification

- `./mvnw compile -DskipTests -f backend/pom.xml` succeeds with all model changes in place
- Migration file exists at `backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql`

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java` — existing domain model to extend
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` — existing domain model to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java` — existing entity to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java` — existing entity to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java` — existing mapper to update
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java` — existing mapper to update
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java` — existing DTO to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java` — existing DTO to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java` — existing DTO to extend
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java` — existing resource with mappers
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — existing service to update

## Expected Output

- `backend/src/main/resources/db/migration/V8__add_owner_participates_and_subscription_id.sql` — new Flyway migration
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java` — ownerParticipates field added
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` — subscriptionId field added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java` — ownerParticipates column added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java` — subscription_id ManyToOne added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java` — mappers updated
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java` — mappers updated
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java` — ownerParticipates field added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java` — ownerParticipates field added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java` — subscriptionId field added
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java` — mappers updated
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — constructor calls updated
