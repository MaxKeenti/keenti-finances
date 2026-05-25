---
id: T03
parent: S05
milestone: M001
key_files:
  - frontend/src/routes/subscriptions/[id]/+page.server.ts
  - frontend/src/routes/subscriptions/[id]/+page.svelte
  - frontend/src/routes/subscriptions/+page.svelte
key_decisions:
  - recordPayment uses plain formData action (not superforms) — same pattern as delete/addMember, consistent with T01/T02 conventions; memberId null maps to 'Owner' label since personal subscriptions have no member row; paymentsByDate groups with $derived rune and sorts dates descending so newest billing period appears first
duration: 
verification_result: passed
completed_at: 2026-05-14T11:30:06.084Z
blocker_discovered: false
---

# T03: Built SvelteKit /subscriptions/[id] detail page with payment recording, member list, token UUID copy, and grouped payment records with PENDING→PAID status flow

**Built SvelteKit /subscriptions/[id] detail page with payment recording, member list, token UUID copy, and grouped payment records with PENDING→PAID status flow**

## What Happened

Created two new files:

1. `frontend/src/routes/subscriptions/[id]/+page.server.ts` — load function fetches subscription detail, members list, and payment records in parallel from the backend. The `recordPayment` action calls `PUT /api/subscriptions/{id}/payments/{paymentId}` to mark a payment PAID. Error handling follows the existing pattern (404/502 with structured `fail()` returns, console.error structured log lines).

2. `frontend/src/routes/subscriptions/[id]/+page.svelte` — detail page with:
   - Header: subscription name, cost (MXN formatted), type and billing cycle badges, next billing date
   - Token UUID section for SHARED subscriptions with clipboard copy button and visual feedback
   - Members section (SHARED only): list with name and shareAmount
   - Payment records section: grouped by billingDate descending, each record shows member name (or 'Owner' for personal/null memberId), amount, status badge (amber=PENDING, green=PAID), paid date when available, and a 'Record Payment' form button for PENDING records using `use:enhance` + sonner toast notifications
   - Back link to /subscriptions

3. Modified `frontend/src/routes/subscriptions/+page.svelte` — wrapped subscription name in each card with an `<a href="/subscriptions/{sub.id}">` link.

Type-check passed with 0 errors (5 pre-existing warnings in other files unrelated to this task).

## Verification

`bun run check` exited 0 (0 errors, 5 pre-existing warnings). Both output files confirmed present via `test -f`. Payment endpoint shape validated against PaymentRecordResource.java (GET list + PUT /{paymentId}).

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run check` | 0 | pass — 0 errors, 5 pre-existing warnings | 2800ms |
| 2 | `test -f 'frontend/src/routes/subscriptions/[id]/+page.svelte'` | 0 | pass | 10ms |
| 3 | `test -f 'frontend/src/routes/subscriptions/[id]/+page.server.ts'` | 0 | pass | 10ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/routes/subscriptions/[id]/+page.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.svelte`
- `frontend/src/routes/subscriptions/+page.svelte`
