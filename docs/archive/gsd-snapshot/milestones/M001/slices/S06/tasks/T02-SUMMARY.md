---
id: T02
parent: S06
milestone: M001
key_files:
  - frontend/src/routes/debts/+page.server.ts
  - frontend/src/routes/debts/+page.svelte
key_decisions:
  - Zod 4 does not support required_error in z.number() — use .positive('message') instead; this is consistent with how all other schemas in the project are written
  - contactId validated as z.coerce.number().positive() so the HTML select's default value of 0 fails validation and the user must pick a real contact
  - No categories fetch needed for debts — DebtRequest only takes contactId, description, totalAmount; categoryId is only required when recording a payment (T03 scope)
duration: 
verification_result: passed
completed_at: 2026-05-14T15:35:04.237Z
blocker_discovered: false
---

# T02: Built SvelteKit /debts CRUD page with debt cards showing debtor name, MXN balance breakdown, status badge, and create/edit/delete dialogs backed by superforms + Zod 4

**Built SvelteKit /debts CRUD page with debt cards showing debtor name, MXN balance breakdown, status badge, and create/edit/delete dialogs backed by superforms + Zod 4**

## What Happened

Created two files following the established subscriptions/transactions CRUD pattern. The server file defines a Zod 4 schema (contactId positive, description min 1, totalAmount positive) and a load function that fetches /api/debts and /api/contacts in parallel from the backend. Three form actions — create (POST /api/debts), update (PUT /api/debts/{id}), delete (DELETE /api/debts/{id}) — use superforms + zod4 adapter and return structured JSON error bodies for 400/404/502 cases with structured console logs on each operation. The Svelte page renders a card grid where each card shows contactName, description, totalAmount/totalPaid/remaining formatted as MXN via Intl.NumberFormat('es-MX'), a status badge (ACTIVE=amber, PAID=green), and remaining colored differently by status. Create button opens a dialog with contact selector dropdown, description textarea, and totalAmount input. Edit button pre-fills the same dialog. Delete button opens a confirmation dialog. Each debt card links to /debts/[id]. Toast notifications via svelte-sonner fire on success or failure. Initial schema used Zod 3-style required_error option which doesn't exist in Zod 4; fixed to use .positive() message only. bun run check exits 0 with 0 errors (6 warnings are pre-existing across all other pages).

## Verification

Ran `cd frontend && bun run check` — exits 0 with 0 errors. Confirmed both output files exist via `test -f` commands.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run check` | 0 | pass | 13000ms |
| 2 | `test -f frontend/src/routes/debts/+page.svelte` | 0 | pass | 10ms |
| 3 | `test -f frontend/src/routes/debts/+page.server.ts` | 0 | pass | 5ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/routes/debts/+page.server.ts`
- `frontend/src/routes/debts/+page.svelte`
