---
status: accepted
supersedes: 0006
---

# Billing is generated on demand per Subscription, not by a scheduler

Payment Records are created by a **Generate billing** button on the Subscription detail page, which calls `POST /api/subscriptions/{id}/generate-billing` → `BillingService.generateForSubscription(id)`. There is no background job. This replaces the daily Quarkus `@Scheduled` cron of ADR-0006.

The cron was unreliable in practice: the Railway two-service topology (ADR-0007) means the backend can restart or idle, so a once-a-day `0 0 1 * * ?` trigger could silently never run, leaving Subscriptions un-billed with no signal. The "manual `POST` trigger" ADR-0006 promised had also diverged into `generateBillingManual()` — a separate path over `findAll()` that advanced **every** Subscription's `nextBillingDate` on each click, corrupting future dates.

## Behaviour

- **Backfill to today in one click.** Generating catches up: a single click creates a record set for **every** period from `nextBillingDate` up to and including the current period (any `billingDate <= today`), then advances `nextBillingDate` to the first period strictly after today. This is what makes per-Subscription payment history browsable — past periods are generated in one action rather than one click per month. A safety bound (`MAX_CATCH_UP_PERIODS`) stops a corrupted far-past `nextBillingDate` from looping unbounded.
- **Nothing ahead of today.** A Subscription whose `nextBillingDate` is in the future generates nothing until it falls due. This replaces the earlier "a click must always do something, regardless of date" rule (and its no-lead-window framing): billing future periods early served no purpose once catch-up is automatic, and not pre-billing keeps the history aligned with reality.
- **Idempotent per period.** A Payment Record is never duplicated for the same `(subscription, billingDate, member)` tuple, and `nextBillingDate` only advances when the catch-up loop actually ran. Re-triggering an already-caught-up Subscription is a no-op, not a runaway that keeps rolling the date forward.
- **Per Subscription, caller-scoped.** It runs inside the HTTP request, so `UserScopedInterceptor` (ADR-0018) has already engaged the `userScope` + `softDelete` Hibernate filters. `findById` therefore resolves only a non-deleted Subscription owned by the caller; an unknown/foreign/trashed id returns 404. No manual `enableFilter` juggling is needed — the fragility that forced the cron to manage `softDelete` itself is gone with the cron.

## Consequences

- Catching up is a single user action: clicking **Generate billing** backfills every missed period at once, so the detail page can show a month-by-month payment history. Members no longer get an automatic 7-day-ahead view; the upcoming period appears once it is due and the owner generates it.
- `Subscription` no longer needs the `findWithNextBillingDateBefore` lead-window query for billing; it remains only if some other reader uses it.
- The `quarkus-scheduler` dependency and `quarkus.scheduler.enabled` config were removed.

## Considered options

- **Fix the cron instead (Railway cron service / external pinger):** rejected — adds an always-on component and external scheduling infrastructure for a single-user-cadence task the owner is happy to trigger by hand.
- **Keep a global "generate for all my Subscriptions" button (the prior list-page button):** rejected — coarser than needed and it was the home of the date-corruption bug; a per-Subscription button matches where the user is looking (the detail page) and scopes the blast radius to one Subscription.
