---
status: accepted
supersedes: 0006
---

# Billing is generated on demand per Subscription, not by a scheduler

Payment Records are created by a **Generate billing** button on the Subscription detail page, which calls `POST /api/subscriptions/{id}/generate-billing` → `BillingService.generateForSubscription(id)`. There is no background job. This replaces the daily Quarkus `@Scheduled` cron of ADR-0006.

The cron was unreliable in practice: the Railway two-service topology (ADR-0007) means the backend can restart or idle, so a once-a-day `0 0 1 * * ?` trigger could silently never run, leaving Subscriptions un-billed with no signal. The "manual `POST` trigger" ADR-0006 promised had also diverged into `generateBillingManual()` — a separate path over `findAll()` that advanced **every** Subscription's `nextBillingDate` on each click, corrupting future dates.

## Behaviour

- **On demand, any date.** Generating works regardless of how far off `nextBillingDate` is — there is no 7-day lead window. A manual click must always do something visible (the old lead window made the button a no-op for Subscriptions billed more than a week out — the bug recorded in quick-fix Q10).
- **Idempotent per period.** A Payment Record is never duplicated for the same `(subscription, billingDate, member)` tuple, and `nextBillingDate` advances by one cycle **only when at least one record was created**. Re-triggering an already-generated period is a no-op, not a runaway that keeps rolling the date forward.
- **Per Subscription, caller-scoped.** It runs inside the HTTP request, so `UserScopedInterceptor` (ADR-0018) has already engaged the `userScope` + `softDelete` Hibernate filters. `findById` therefore resolves only a non-deleted Subscription owned by the caller; an unknown/foreign/trashed id returns 404. No manual `enableFilter` juggling is needed — the fragility that forced the cron to manage `softDelete` itself is gone with the cron.

## Consequences

- Catching up is now a user action: if a period is missed, the user clicks **Generate billing** (once per period). Members no longer get an automatic 7-day-ahead view; the upcoming period appears when the owner generates it.
- `Subscription` no longer needs the `findWithNextBillingDateBefore` lead-window query for billing; it remains only if some other reader uses it.
- The `quarkus-scheduler` dependency and `quarkus.scheduler.enabled` config were removed.

## Considered options

- **Fix the cron instead (Railway cron service / external pinger):** rejected — adds an always-on component and external scheduling infrastructure for a single-user-cadence task the owner is happy to trigger by hand.
- **Keep a global "generate for all my Subscriptions" button (the prior list-page button):** rejected — coarser than needed and it was the home of the date-corruption bug; a per-Subscription button matches where the user is looking (the detail page) and scopes the blast radius to one Subscription.
