---
id: T02
parent: S03
milestone: M002
key_files:
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
  - backend/src/main/java/com/keenti/finances/application/service/BillingService.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
key_decisions:
  - recalculateShares is triggered only when ownerParticipates changes AND type is SHARED to avoid unnecessary recalculation
  - BillingResource injects BillingService directly (not via a port/use-case interface) for simplicity, consistent with how the scheduler accesses it
  - The private recalculateShares(Long, Subscription) overload avoids a second DB fetch when called from update() which already has the updated Subscription object
duration: 
verification_result: passed
completed_at: 2026-05-17T07:09:08.714Z
blocker_discovered: false
---

# T02: Fixed billing split math for ownerParticipates in add/remove/update member, added recalculateShares, extracted BillingService, and added BillingResource POST endpoint

**Fixed billing split math for ownerParticipates in add/remove/update member, added recalculateShares, extracted BillingService, and added BillingResource POST endpoint**

## What Happened

Made the following changes across the backend:

1. SubscriptionService.addMember: Changed divisor from `existing.size() + 1` to `memberCount + (sub.isOwnerParticipates() ? 1 : 0)` so the owner slot is counted when ownerParticipates is true.

2. SubscriptionService.removeMember: Applied same divisor logic using `remaining.size() + (sub.isOwnerParticipates() ? 1 : 0)` after removing the member.

3. SubscriptionService.update: Added detection of ownerParticipates flag change; when it changes on a SHARED subscription, calls recalculateShares to update all member shares with the new divisor. Structured logging includes old/new divisor context.

4. Added recalculateShares(Long subscriptionId) public method (added to SubscriptionUseCase interface) and private overload recalculateShares(Long, Subscription) used internally. Logs at INFO with old divisor, new divisor, ownerParticipates flag, and new share amount.

5. Created BillingService (ApplicationScoped) that contains the extracted generateBilling() method with all the logic previously in SubscriptionBillingScheduler. Logs `billing.generate` with count of records created.

6. Updated SubscriptionBillingScheduler to inject BillingService and delegate to billingService.generateBilling(), simplifying the scheduler to a thin cron wrapper.

7. Created BillingResource at POST /api/subscriptions/generate-billing that calls billingService.generateBilling() and returns {generated: int}.

Build verified with ./mvnw compile -DskipTests in 1.891s — BUILD SUCCESS.

## Verification

Ran `./mvnw compile -DskipTests` in backend directory. All 90 source files compiled successfully with no errors. BillingResource POST endpoint exists at /api/subscriptions/generate-billing. recalculateShares method added to SubscriptionUseCase interface and implemented in SubscriptionService.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd /Users/moonstone/Source/Personal/keenti-finances/.gsd/worktrees/M002/backend && ./mvnw compile -DskipTests` | 0 | BUILD SUCCESS — 90 source files compiled | 1891ms |

## Deviations

None — all must-haves implemented as specified.

## Known Issues

None.

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
- `backend/src/main/java/com/keenti/finances/application/service/BillingService.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
