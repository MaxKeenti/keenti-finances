---
estimated_steps: 41
estimated_files: 26
skills_used: []
---

# T01: Flyway V4 migration + full hexagonal subscription backend with scheduler

## Description

Build the complete backend for subscription management: database tables, domain models, hexagonal ports and adapters, application services, REST resources, and the daily billing scheduler. This follows the exact patterns established in S02 (Category/Contact) and S03 (Transaction).

## Steps

1. Add `quarkus-scheduler` dependency to `backend/pom.xml`
2. Create Flyway V4 migration with three tables:
   - `subscription` (id BIGSERIAL PK, name VARCHAR(200) NOT NULL, cost DECIMAL(12,2) NOT NULL, billing_cycle VARCHAR(10) NOT NULL CHECK IN ('MONTHLY','YEARLY'), type VARCHAR(10) NOT NULL CHECK IN ('PERSONAL','SHARED'), category_id BIGINT REFERENCES category(id), next_billing_date DATE NOT NULL, token_uuid VARCHAR(36) UNIQUE, created_at TIMESTAMP DEFAULT NOW())
   - `subscription_member` (id BIGSERIAL PK, subscription_id BIGINT NOT NULL REFERENCES subscription(id) ON DELETE CASCADE, contact_id BIGINT NOT NULL REFERENCES contact(id), share_amount DECIMAL(12,2) NOT NULL, UNIQUE(subscription_id, contact_id), created_at TIMESTAMP DEFAULT NOW())
   - `payment_record` (id BIGSERIAL PK, subscription_id BIGINT NOT NULL REFERENCES subscription(id) ON DELETE CASCADE, member_id BIGINT REFERENCES subscription_member(id) ON DELETE CASCADE, billing_date DATE NOT NULL, amount DECIMAL(12,2) NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'PENDING' CHECK IN ('PENDING','PAID'), paid_date DATE, created_at TIMESTAMP DEFAULT NOW())
3. Create domain POJOs (no framework imports): Subscription, SubscriptionMember, PaymentRecord
4. Create port interfaces: SubscriptionUseCase, PaymentRecordUseCase (in ports), SubscriptionRepository, SubscriptionMemberRepository, PaymentRecordRepository (out ports)
5. Create SubscriptionService implementing SubscriptionUseCase: CRUD for subscriptions, auto-generate UUID token for SHARED type, member add/remove with share recalculation (equal split: cost / memberCount), validate type constraints (PERSONAL subs cannot have members)
6. Create PaymentRecordService implementing PaymentRecordUseCase: list payment records by subscription, record payment (set status=PAID, paidDate=today)
7. Create SubscriptionBillingScheduler: @Scheduled daily job that finds subscriptions with nextBillingDate <= today + 7 days that don't already have a payment record for that billing date, creates PENDING records (one per member for SHARED, one with null member for PERSONAL), advances nextBillingDate
8. Create Panache entities: SubscriptionEntity, SubscriptionMemberEntity, PaymentRecordEntity
9. Create Panache repository adapters implementing the out-ports
10. Create REST resources: SubscriptionResource at /api/subscriptions (full CRUD + GET/POST/DELETE for /{id}/members), PaymentRecordResource at /api/subscriptions/{id}/payments (GET list, PUT /{paymentId} to record payment)
11. Create request/response records: SubscriptionRequest, SubscriptionResponse, MemberRequest, MemberResponse, PaymentRecordResponse
12. Add scheduler config to application.properties: `quarkus.scheduler.enabled=true`

## Must-Haves

- Domain models must be framework-free POJOs (no Jakarta/Panache imports)
- Shared subscriptions auto-generate UUID token; personal subscriptions have null token
- Equal split: share_amount = subscription.cost / memberCount, recalculated on member add/remove
- Scheduler is idempotent: checks for existing payment_record before creating
- Structured JSON error bodies for 400/404/409 responses
- JBoss Logger structured logging on all operations

## Verification

`./mvnw compile -q` exits 0 AND domain model grep for jakarta/panache imports returns no matches AND `test -f backend/src/main/resources/db/migration/V4__create_subscription_tables.sql`

## Threat Surface

- Abuse: Member addition to non-SHARED subscription must be rejected; scheduler must not duplicate payment records
- Data exposure: token_uuid is the access credential for S07 public view — treat as sensitive
- Input trust: subscription name, cost, billing_cycle, type all reach DB — validate at service layer

## Failure Modes

| Dependency | On error | On timeout | On malformed response |
|------------|----------|-----------|----------------------|
| PostgreSQL | Panache throws, service returns 500 | Connection pool timeout, 503 | N/A (typed queries) |
| Scheduler | Log error, retry on next daily run | N/A (in-process) | N/A |

## Negative Tests

- Adding member to PERSONAL subscription → 400
- Creating subscription with invalid billing_cycle → 400
- Recording payment on already-PAID record → 409
- Scheduler run when records already exist for billing_date → no duplicates

## Inputs

- `backend/pom.xml — add quarkus-scheduler dependency`
- `backend/src/main/resources/application.properties — add scheduler config`
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java — reference for domain POJO pattern`
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java — reference for domain POJO pattern`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java — reference for service pattern`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java — reference for REST resource pattern`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java — reference for Panache entity pattern`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java — reference for Panache adapter pattern`
- `backend/src/main/resources/db/migration/V3__create_transaction_table.sql — reference for migration pattern`

## Expected Output

- `backend/src/main/resources/db/migration/V4__create_subscription_tables.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`
- `backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java`
- `backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/PaymentRecordUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionMemberRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/PaymentRecordRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
- `backend/src/main/java/com/keenti/finances/application/service/PaymentRecordService.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionMemberEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PaymentRecordEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionMemberRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanachePaymentRecordRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java`

## Verification

./mvnw compile -q && ! grep -rq 'import jakarta\|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/model/Subscription.java backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java && test -f backend/src/main/resources/db/migration/V4__create_subscription_tables.sql
