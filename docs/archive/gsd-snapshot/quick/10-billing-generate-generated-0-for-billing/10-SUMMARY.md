# Quick Task: [billing.generate] generated=0
for **Billing trigger:** In a shared subscription, the "Generate Bills" action creates payment records for members

**Date:** 2026-05-18
**Branch:** gsd/quick/10-billing-generate-generated-0-for-billing

## What Changed
- Root cause: `BillingService.generateBilling()` used a 7-day lead time cutoff (`nextBillingDate <= today + 7`). When a user manually clicked "Generate Billing" on a subscription whose next billing date was more than 7 days away, no subscriptions were found and 0 records were created.
- Extracted the billing-record creation loop into `generateForSubscriptions(List<Subscription>)` to avoid duplication.
- Added `generateBillingManual()` which processes ALL subscriptions without a date cutoff.
- Updated `BillingResource.generateBilling()` (the REST endpoint) to call `generateBillingManual()`.
- The scheduler (`SubscriptionBillingScheduler`) continues to use the original `generateBilling()` with its 7-day lead time.

## Files Modified
- `backend/src/main/java/com/keenti/finances/application/service/BillingService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/BillingResource.java`

## Verification
- Backend Maven build: SUCCESS (clean compile)
- Backend tests: 12/12 pass, 0 failures
