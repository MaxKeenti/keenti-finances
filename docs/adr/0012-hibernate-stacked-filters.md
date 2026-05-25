---
status: accepted
---

# Hibernate stacked `@Filter`s for user-scope and soft-delete, enabled by a JAX-RS `ContainerRequestFilter`

Two Hibernate `@FilterDef`s sit on every scoped entity: `userScope` (`user_id = :currentUser`) and `softDelete` (`deleted_at IS NULL`). A single JAX-RS `ContainerRequestFilter` (`@Provider`) reads `X-WorkOS-User-Id`, resolves/provisions the local `app_user`, populates `UserContext`, and enables both filters on the Hibernate `Session`. Requests to non-public paths without the header return `401`.

Defense-in-depth: a forgotten `WHERE` clause cannot leak cross-user or soft-deleted rows because the filter is applied at the Session level. The 3 native SQL queries that bypass Panache get explicit `WHERE user_id = ?` clauses — the only places where developers must remember the rule.

## Consequences

- `PublicSubscriptionResource` and `SubscriptionBillingScheduler` have no HTTP request context, so the filter is never enabled for them — they read unfiltered and must scope manually.
- Writes are not covered by Hibernate filters. Repository `toEntity()` methods inject `UserContext.userId` on save/update — see ADR-0014.
