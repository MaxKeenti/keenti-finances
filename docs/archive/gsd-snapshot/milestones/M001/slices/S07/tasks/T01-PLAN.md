---
estimated_steps: 26
estimated_files: 6
skills_used: []
---

# T01: Add public subscription REST endpoint with token-based lookup

## Description

Add a `findByTokenUuid(String tokenUuid)` method to the SubscriptionRepository port and its Panache implementation. Create a new `PublicSubscriptionResource` at `/api/public/subscriptions/{token}` that looks up a subscription by its UUID token (no auth), loads its members with contact names and payment records, and returns a composite JSON response. Invalid tokens return 404.

## Steps

1. Add `Optional<Subscription> findByTokenUuid(String tokenUuid)` to `SubscriptionRepository` port interface
2. Implement `findByTokenUuid` in `PanacheSubscriptionRepository` using `find("tokenUuid", tokenUuid).firstResultOptional()` mapped through `toDomain`
3. Add `Optional<Subscription> getByToken(String tokenUuid)` to `SubscriptionUseCase` interface
4. Implement `getByToken` in `SubscriptionService` delegating to repository
5. Create `PublicSubscriptionResponse` record with fields: subscriptionName, cost, billingCycle, nextBillingDate, and a list of member payment summaries
6. Create `PublicSubscriptionResource` at `@Path("/api/public/subscriptions")` with a single `@GET @Path("/{token}")` method that:
   - Calls `subscriptionUseCase.getByToken(token)`
   - Returns 404 with `{"error":"Subscription not found"}` if empty
   - Loads members via `subscriptionUseCase.listMembers(id)`, resolves contact names via `contactUseCase.getById`
   - Loads payments via `paymentRecordUseCase.listBySubscription(id)`
   - Returns composite response with subscription info + members + payments
7. Log token lookup results via JBoss Logger

## Must-Haves

- [ ] `findByTokenUuid` added to repository port and Panache implementation
- [ ] `getByToken` added to use case port and service
- [ ] `PublicSubscriptionResource` serves GET at `/api/public/subscriptions/{token}`
- [ ] Invalid tokens return 404 JSON response
- [ ] No auth annotations — endpoint is fully public

## Verification

- `./mvnw compile -q` exits 0
- `grep -q 'findByTokenUuid' backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`
- `grep -q 'api/public/subscriptions' backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java`
- `grep -q 'getByToken' backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`
- `backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java`
- `backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/PaymentRecordUseCase.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResource.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java`

## Expected Output

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java`

## Verification

./mvnw compile -q -f backend/pom.xml && grep -q 'findByTokenUuid' backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java && grep -q 'getByToken' backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java && test -f backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java
