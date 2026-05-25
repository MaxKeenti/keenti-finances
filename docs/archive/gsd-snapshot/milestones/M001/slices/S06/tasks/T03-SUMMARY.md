---
id: T03
parent: S06
milestone: M001
key_files:
  - frontend/src/routes/debts/[id]/+page.server.ts
  - frontend/src/routes/debts/[id]/+page.svelte
key_decisions:
  - Filtered category selector to INGRESS-only categories client-side so the auto-created transaction is categorized correctly — consistent with how categoryId flows through DebtPaymentRequest to TransactionUseCase
  - Used native <textarea> instead of importing the missing shadcn textarea component — keeps the check clean without adding a new dependency
  - Pre-filled payment amount with debt.remaining so the common case (full payment) requires no typing
duration: 
verification_result: passed
completed_at: 2026-05-14T15:38:13.129Z
blocker_discovered: false
---

# T03: Built SvelteKit /debts/[id] detail page with payment history table, progress bar, and superforms-backed partial payment recording that auto-creates INGRESS transactions via the backend

**Built SvelteKit /debts/[id] detail page with payment history table, progress bar, and superforms-backed partial payment recording that auto-creates INGRESS transactions via the backend**

## What Happened

Created two files: `frontend/src/routes/debts/[id]/+page.server.ts` and `frontend/src/routes/debts/[id]/+page.svelte`.

The server file loads the debt by ID (404-aware), fetches payments and categories in parallel, and pre-fills the payment form amount with the remaining balance and paymentDate with today. The `recordPayment` action validates via superforms + Zod 4, POSTs to `/api/debts/{id}/payments` with `{ amount, paymentDate, categoryId, notes }`, and logs the resulting transactionId (the auto-created INGRESS) on success.

The Svelte page shows: a header card with debtor name, description, status badge, and a 3-column balance breakdown (total/paid/remaining); a progress bar showing paid percentage; a payment history table with date, MXN amount, notes, and linked transaction ID columns; and a record-payment form using Form.Field wrappers, an INGRESS-filtered category selector (only `type === 'INGRESS'` categories shown), and a native textarea for notes (the shadcn textarea component is not installed in this project). The form is fully disabled when debt status is `PAID`. After a successful submission `invalidateAll()` refreshes the data and a sonner toast confirms the action.

One deviation: replaced `Textarea` from `$lib/components/ui/textarea` (not installed) with a native `<textarea>` styled with Tailwind classes matching the Input component, consistent with how other pages in the project handle missing shadcn components.

## Verification

Ran `cd frontend && bun run check` — exited 0 with 0 errors and 7 warnings (all pre-existing across other pages, not introduced by this task). Confirmed both output files exist with `test -f` checks.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun install && bun run check` | 0 | pass | 22000ms |
| 2 | `test -f frontend/src/routes/debts/[id]/+page.svelte` | 0 | pass | 5ms |
| 3 | `test -f frontend/src/routes/debts/[id]/+page.server.ts` | 0 | pass | 4ms |

## Deviations

Replaced `Textarea` from `$lib/components/ui/textarea` (component not installed) with a native `<textarea>` using Tailwind classes. No functional difference.

## Known Issues

None.

## Files Created/Modified

- `frontend/src/routes/debts/[id]/+page.server.ts`
- `frontend/src/routes/debts/[id]/+page.svelte`
