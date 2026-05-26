---
status: accepted
---

# Hibernate filter activation lives in a CDI interceptor, not the request filter

The `userScope` and `softDelete` Hibernate `@Filter`s are activated by `UserScopedInterceptor` — a CDI `@AroundInvoke` interceptor at `Interceptor.Priority.APPLICATION` — applied to each root-entity `Panache*Repository`. The interceptor enables both filters on the Hibernate session that's open *inside the resource method's `@Transactional` scope*. It is not enabled in `UserScopeFilter` (the JAX-RS `ContainerRequestFilter` that resolves the WorkOS identity and sets `UserContext`).

The naive placement — `session.enableFilter(...)` inside `UserScopeFilter.filter()` — looks correct but silently fails. The filter method is itself `@Transactional`, so it opens its own session, enables the filter on that session, then the session closes when the method returns. By the time the resource method runs (under its own `@Transactional`), it's a fresh session with no filters set. Queries return rows for every user. This was the cause of the 2026-05-25 production data leak between `user_id=1` and `user_id=2`.

Moving activation into an interceptor that runs *after* `@Transactional` has opened the request's session (Quarkus's `@Transactional` is at priority 200; `APPLICATION` is 2000, so we wrap inside it) puts the `enableFilter` calls on the same session the queries run against. The interceptor is idempotent (`getEnabledFilter == null` guard) so repeated calls within a session are safe, and the cron path (`SubscriptionBillingScheduler`) which enables `softDelete` itself still works without conflict.

## Considered options

- **Activate in `UserScopeFilter` (the JAX-RS filter):** rejected — the cross-transaction session boundary makes the `enableFilter` call a no-op, which is exactly the bug that hit prod.
- **Inline `enableFilter` at the top of every repository method:** rejected — equivalent behaviour, but ~25 mechanical call sites that are easy to forget when a new method is added. The interceptor enforces it structurally.
- **Add explicit `WHERE user_id = :userId` to every Panache query:** rejected — duplicates what `@Filter` exists for, and we'd lose `softDelete` for free unless we duplicated that too. Native-SQL paths like `DashboardService` already do this where they bypass Hibernate.
- **Use `org.hibernate.event.spi.SessionEventListener` to auto-enable filters on session open:** rejected — the listener fires outside a CDI request scope so it can't inject `UserContext`. Workarounds (thread-locals, etc.) reintroduce the same fragility we just removed.

## Adding new repositories

Any new `@ApplicationScoped` repository that queries a *root* entity (`Category`, `Contact`, `Transaction`, `Subscription`, `Debt`) must be annotated with `@UserScoped`. Child-entity repositories (`DebtPayment`, `SubscriptionMember`, `PaymentRecord`) do not need it — per ADR-0014 children have no `user_id` and `softDelete`; their visibility is governed by traversing the parent, whose filter is engaged.
