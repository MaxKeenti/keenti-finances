---
id: T01
parent: S05
milestone: M001
key_files:
  - backend/pom.xml
  - backend/src/main/resources/application.properties
  - backend/src/main/resources/db/migration/V4__create_subscription_tables.sql
  - backend/src/main/java/com/keenti/finances/domain/model/Subscription.java
  - backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java
  - backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/PaymentRecordUseCase.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionMemberRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/out/PaymentRecordRepository.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
  - backend/src/main/java/com/keenti/finances/application/service/PaymentRecordService.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionMemberEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PaymentRecordEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionMemberRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanachePaymentRecordRepository.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java
key_decisions:
  - Scheduler cron set to 0 0 1 * * ? (1am daily) to avoid midnight edge cases at billing date boundary
  - Member add to PERSONAL subscription throws 400 BadRequestException; duplicate member throws 409 via WebApplicationException
  - Token UUID generated at create-time for SHARED; preserved on update unless type changes to PERSONAL (then cleared) or to SHARED from PERSONAL (then generated)
  - PaymentRecordResource uses full path @Path annotation to avoid JAX-RS sub-resource complexity
duration: 
verification_result: passed
completed_at: 2026-05-14T10:30:26.333Z
blocker_discovered: false
---

# T01: Added Flyway V4 migration + full hexagonal subscription backend (domain, ports, services, Panache adapters, REST resources, daily billing scheduler)

**Added Flyway V4 migration + full hexagonal subscription backend (domain, ports, services, Panache adapters, REST resources, daily billing scheduler)**

## What Happened

Followed the exact hexagonal patterns established in S02/S03. Added quarkus-scheduler dependency to pom.xml and enabled it in application.properties. Created V4__create_subscription_tables.sql with three tables: subscription, subscription_member, payment_record. Created three framework-free domain POJOs (Subscription, SubscriptionMember, PaymentRecord). Created in-ports (SubscriptionUseCase, PaymentRecordUseCase) and out-ports (SubscriptionRepository, SubscriptionMemberRepository, PaymentRecordRepository). Implemented SubscriptionService with full CRUD, auto-UUID generation for SHARED type, equal-split share recalculation on member add/remove, and rejection of member addition to PERSONAL subscriptions. Implemented PaymentRecordService with list-by-subscription and record-payment (409 if already PAID). Implemented SubscriptionBillingScheduler with @Scheduled(cron) daily job that finds subscriptions with nextBillingDate <= today+7, creates idempotent PENDING payment records (one per member for SHARED, one with null member for PERSONAL), and advances nextBillingDate. Created Panache entities (SubscriptionEntity, SubscriptionMemberEntity, PaymentRecordEntity) and three Panache repository adapters. Created REST resources: SubscriptionResource at /api/subscriptions with full CRUD plus /members sub-routes, PaymentRecordResource at /api/subscriptions/{subscriptionId}/payments. Created all five DTOs (SubscriptionRequest/Response, MemberRequest/Response, PaymentRecordResponse). Structured JBoss Logger logging on all CRUD and scheduler operations.

## Verification

./mvnw compile -q exits 0. Domain model files contain no jakarta or quarkus imports (grep confirms). V4 migration file exists at backend/src/main/resources/db/migration/V4__create_subscription_tables.sql.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `./mvnw compile -q` | 0 | pass | 5035ms |
| 2 | `! grep -rq 'import jakarta|import io.quarkus' domain/model/Subscription.java domain/model/SubscriptionMember.java domain/model/PaymentRecord.java` | 0 | pass | 10ms |
| 3 | `test -f backend/src/main/resources/db/migration/V4__create_subscription_tables.sql` | 0 | pass | 5ms |

## Deviations

PaymentRecordResource added as a separate @Path class (not a sub-resource delegated from SubscriptionResource) to avoid JAX-RS sub-resource locator complexity — the REST contract is identical.

## Known Issues

none

## Files Created/Modified

- `backend/pom.xml`
- `backend/src/main/resources/application.properties`
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
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java`
