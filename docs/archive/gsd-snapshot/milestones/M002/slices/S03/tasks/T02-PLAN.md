---
estimated_steps: 7
estimated_files: 5
skills_used: []
---

# T02: Fix billing split math for owner participation and add manual billing endpoint

**Slice:** S03 — Subscription Model Improvements
**Milestone:** M002

## Description

The core business logic change — split divisor must account for ownerParticipates flag. Manual billing endpoint reuses scheduler logic so users don't wait for the 1am cron.

## Steps

1. In SubscriptionService.addMember: change divisor from `existing.size() + 1` to `memberCount + (sub.isOwnerParticipates() ? 1 : 0)` where memberCount includes the new member.
2. In SubscriptionService.removeMember: apply same divisor logic using remaining.size() + owner flag.
3. In SubscriptionService.update: when ownerParticipates changes, recalculate all member shares with new divisor.
4. Add recalculateShares(Long subscriptionId) method to SubscriptionService that fetches members and updates all shares based on current ownerParticipates.
5. Extract billing generation logic from SubscriptionBillingScheduler.generateUpcomingPaymentRecords into a new BillingService.generateBilling() method. Scheduler delegates to it.
6. Create BillingResource (POST /api/subscriptions/generate-billing) that calls BillingService.generateBilling() and returns {generated: int}.
7. Add BillingService interface to domain/port/in if following port pattern, or keep as ApplicationScoped service.

## Must-Haves

- [ ] Split divisor accounts for ownerParticipates flag in add/remove/update member
- [ ] recalculateShares method exists and is called when ownerParticipates changes
- [ ] BillingService extracts billing logic from scheduler
- [ ] BillingResource POST endpoint exists at /api/subscriptions/generate-billing
- [ ] ./mvnw compile -DskipTests succeeds

## Verification

- `./mvnw compile -DskipTests -f backend/pom.xml` succeeds
- BillingResource endpoint exists with POST mapping

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — T01 output with ownerParticipates wired through
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java` — existing scheduler with billing logic to extract
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java` — T01 output with ownerParticipates getter

## Expected Output

- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — split math fixed, recalculateShares added
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java` — delegates to BillingService
- `backend/src/main/java/com/keenti/finances/application/service/BillingService.java` — new service with extracted billing logic
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java` — new REST endpoint
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java` — recalculateShares added if using port pattern
