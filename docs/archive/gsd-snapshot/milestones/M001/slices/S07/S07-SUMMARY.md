---
id: S07
parent: M001
milestone: M001
provides:
  - GET /api/public/subscriptions/{token} — unauthenticated endpoint returning subscription name, member list, and payment records by billing date
  - SvelteKit /public/subscription/[token] — read-only public page rendering member payment status without login
  - Auth bypass via PUBLIC_PATHS prefix in hooks.server.ts
requires:
  - slice: S05
    provides: Subscription.tokenUuid domain model, SubscriptionRepository port, PaymentRecordUseCase.listBySubscription, SubscriptionUseCase.listMembers
affects:
  - S08
key_files:
  - backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java
  - frontend/src/hooks.server.ts
  - frontend/src/routes/public/subscription/[token]/+page.server.ts
  - frontend/src/routes/public/subscription/[token]/+page.svelte
key_decisions:
  - Used Panache find('tokenUuid', tokenUuid).firstResultOptional() for token lookup — consistent with existing findById pattern
  - PublicSubscriptionResponse uses nested records (MemberPaymentSummary, PaymentSummary) for clean JSON serialization without extra DTOs
  - No @RolesAllowed or @Authenticated on PublicSubscriptionResource — endpoint is intentionally unauthenticated
  - Token-found and token-not-found both logged at INFO via JBoss Logger for audit trail
  - PUBLIC_PATHS prefix match ('/public') rather than per-route bypass — all future /public/* routes auto-bypass auth
  - Isolated public page layout implemented inline in +page.svelte (min-h-screen container) instead of a separate +layout.svelte file
patterns_established:
  - Unauthenticated Quarkus REST endpoint: omit @RolesAllowed/@Authenticated; use nested Java records for composite response DTOs
  - SvelteKit PUBLIC_PATHS prefix bypass in hooks.server.ts covers all sub-routes without per-file changes
observability_surfaces:
  - JBoss Logger INFO log on every token lookup (found/not-found) in PublicSubscriptionResource
drill_down_paths:
  - .gsd/milestones/M001/slices/S07/tasks/T01-SUMMARY.md
  - .gsd/milestones/M001/slices/S07/tasks/T02-SUMMARY.md
duration: ""
verification_result: passed
completed_at: 2026-05-14T20:39:17.917Z
blocker_discovered: false
---

# S07: Public Subscription View

**Added unauthenticated /api/public/subscriptions/{token} REST endpoint and SvelteKit /public/subscription/[token] read-only page with auth bypass — invalid tokens return 404 on both backend and frontend**

## What Happened

S07 delivered a public, login-free view of shared subscription payment status via two tasks.

T01 extended the hexagonal backend: `findByTokenUuid` was added to `SubscriptionRepository` port and `PanacheSubscriptionRepository` implementation; `getByToken` was added to `SubscriptionUseCase` port and `SubscriptionService`. A new `PublicSubscriptionResource` at `/api/public/subscriptions/{token}` (no `@RolesAllowed` / `@Authenticated`) returns a `PublicSubscriptionResponse` composed of nested `MemberPaymentSummary` and `PaymentSummary` records. Token lookups (found and not-found) are logged at INFO level via JBoss Logger. Invalid tokens return 404 with a structured JSON error body consistent with existing patterns.

T02 built the SvelteKit side: `hooks.server.ts` was updated to add `'/public'` to `PUBLIC_PATHS`, so all `/public/*` routes bypass the auth guard without per-route changes. A `+page.server.ts` load function fetches the public endpoint and throws a SvelteKit 404 error on invalid tokens. A `+page.svelte` renders the subscription name, member list, and per-member payment status grouped by billing period — entirely without login. The page uses a full-screen isolated layout (`min-h-screen bg-background py-10 px-4`) inline rather than a separate `+layout.svelte`.

## Verification

1. `./mvnw compile -q` from `backend/` exits 0 — no compilation errors.
2. `grep -q 'findByTokenUuid' SubscriptionRepository.java` passes.
3. `grep -q 'getByToken' SubscriptionUseCase.java` passes.
4. `PublicSubscriptionResource.java` exists on disk.
5. `grep -q '/public' hooks.server.ts` passes.
6. `frontend/src/routes/public/subscription/[token]/+page.server.ts` exists.
7. `frontend/src/routes/public/subscription/[token]/+page.svelte` exists.
8. `bun run check` shows zero errors in S07 files (70 pre-existing baseline errors in unrelated files unchanged).

## Requirements Advanced

None.

## Requirements Validated

None.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

"+layout.svelte for the public route was not created — the isolation layout was implemented inline in +page.svelte using a full-screen container div, achieving the same visual separation without an extra file."

## Known Limitations

Full end-to-end verification (live browser, seeded DB) deferred to S08 Railway deployment. The 70 pre-existing svelte-check errors in unrelated files (session.ts, login, categories pages) are not addressed in S07.

## Follow-ups

none

## Files Created/Modified

- `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java` — Added findByTokenUuid port method
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java` — Implemented findByTokenUuid via Panache
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java` — Added getByToken use case port method
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java` — Implemented getByToken service method
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java` — New unauthenticated REST endpoint at /api/public/subscriptions/{token}
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java` — New composite response DTO with nested MemberPaymentSummary and PaymentSummary records
- `frontend/src/hooks.server.ts` — Added '/public' to PUBLIC_PATHS auth bypass
- `frontend/src/routes/public/subscription/[token]/+page.server.ts` — New load function fetching public endpoint; throws 404 on invalid token
- `frontend/src/routes/public/subscription/[token]/+page.svelte` — New read-only public page rendering subscription info and member payment status
