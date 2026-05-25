---
id: T01
parent: S07
milestone: M001
key_files:
  - backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java
key_decisions:
  - Used Panache find("tokenUuid", tokenUuid).firstResultOptional() for token lookup — consistent with existing findById pattern
  - PublicSubscriptionResponse uses nested records (MemberPaymentSummary, PaymentSummary) for clean JSON serialization without extra DTOs
  - No @RolesAllowed or @Authenticated annotations on PublicSubscriptionResource — endpoint is intentionally unauthenticated
  - Token-found and token-not-found both logged at INFO level via JBoss Logger for audit trail
duration: 
verification_result: passed
completed_at: 2026-05-14T20:27:54.302Z
blocker_discovered: false
---

# T01: Added public token-based subscription REST endpoint at /api/public/subscriptions/{token} returning composite member+payment JSON with 404 on invalid tokens

**Added public token-based subscription REST endpoint at /api/public/subscriptions/{token} returning composite member+payment JSON with 404 on invalid tokens**

## What Happened

Extended the hexagonal architecture to support unauthenticated token lookups:

1. Added `findByTokenUuid(String tokenUuid): Optional<Subscription>` to the `SubscriptionRepository` port and implemented it in `PanacheSubscriptionRepository` using Panache's `find("tokenUuid", tokenUuid).firstResultOptional()`.

2. Added `getByToken(String tokenUuid): Optional<Subscription>` to `SubscriptionUseCase` and implemented it in `SubscriptionService`, logging the lookup result (found/not-found) via JBoss Logger at info level.

3. Created `PublicSubscriptionResponse` as a record with nested `MemberPaymentSummary` and `PaymentSummary` records, carrying subscriptionName, cost, billingCycle, nextBillingDate, and per-member payment history.

4. Created `PublicSubscriptionResource` at `@Path("/api/public/subscriptions")` with a single `GET /{token}` method. It resolves the subscription by token, returns structured 404 JSON on miss, then loads members via `subscriptionUseCase.listMembers`, resolves contact names via `contactUseCase.getById`, filters payments per member from `paymentRecordUseCase.listBySubscription`, and assembles the composite response. No auth annotations — endpoint is fully public. Both token-found and token-not-found paths are logged via JBoss Logger.

## Verification

Compiled with `./mvnw compile -q` from the backend directory (exit 0). Verified via grep that `findByTokenUuid` exists in the repository port, `getByToken` exists in the use case port, `api/public/subscriptions` path is declared in the resource, and the resource file exists on disk.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw compile -q` | 0 | pass | 12000ms |
| 2 | `grep -q 'findByTokenUuid' backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java` | 0 | pass | 50ms |
| 3 | `grep -q 'getByToken' backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java` | 0 | pass | 50ms |
| 4 | `grep -q 'api/public/subscriptions' backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java` | 0 | pass | 50ms |
| 5 | `test -f backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java` | 0 | pass | 10ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java`
