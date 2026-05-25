---
id: T04
parent: S03
milestone: M002
key_files:
  - frontend/src/routes/subscriptions/+page.server.ts
  - frontend/src/routes/subscriptions/+page.svelte
  - frontend/src/routes/subscriptions/[id]/+page.server.ts
  - frontend/src/routes/subscriptions/[id]/+page.svelte
  - frontend/src/lib/components/native-date-picker/native-date-picker.svelte
key_decisions:
  - Link Transactions dialog uses hidden inputs iterated from a $state Set<number> rather than a superforms schema — consistent with the plain-formData pattern used for delete/addMember/removeMember in this project
  - unlinkedTransactions is computed server-side in the load function by fetching all transactions and filtering where subscriptionId is null and not already in the linked set — no dedicated backend endpoint needed
  - Generate Billing button placed adjacent to New Subscription in the page header to surface it prominently without requiring navigation to a settings page
  - Fixed pre-existing Calendar type=single omission in native-date-picker.svelte to unblock svelte-check threshold-error gate
duration: 
verification_result: passed
completed_at: 2026-05-17T12:07:54.878Z
blocker_discovered: false
---

# T04: Added ownerParticipates toggle, Generate Billing button, and Linked Transactions section with multi-select link dialog to subscription frontend pages.

**Added ownerParticipates toggle, Generate Billing button, and Linked Transactions section with multi-select link dialog to subscription frontend pages.**

## What Happened

Implemented all four frontend changes required by S03/T04:

1. **+page.server.ts (subscriptions list)**: Added `generateBilling` action that POSTs to `/api/subscriptions/generate-billing`, reads the JSON `generated` count from the response, and logs `[billing.generate] generated=N`.

2. **+page.svelte (subscriptions list)**: Added `import * as Select` (fixing a pre-existing missing import for the member dialog), added `ownerParticipates: z.boolean().optional()` to the client-side Zod schema, added `ownerParticipates` to the Subscription type, updated `openCreate` and `openEdit` to include `ownerParticipates`, added a checkbox toggle labeled 'I participate in this subscription' that shows only when `$form.type === 'SHARED'`, and added a 'Generate Billing' button (form POST `?/generateBilling`) next to 'New Subscription' that shows a toast with the generated record count.

3. **[id]/+page.server.ts (detail page)**: Added `ownerParticipates` to the Subscription type, added `TransactionResponse` type, extended the parallel fetch to also call `/api/subscriptions/{id}/linked-transactions` and `/api/transactions`, computed `unlinkedTransactions` (all transactions where `subscriptionId` is null and not already in the linked set), and added a `linkTransactions` action that iterates selected transaction IDs and calls `PUT /api/transactions/{id}/link-subscription` for each, logging `[transaction.link] subscriptionId=X count=N ids=...`.

4. **[id]/+page.svelte (detail page)**: Added Dialog import, TransactionResponse type, link dialog state (`linkDialogOpen`, `selectedTxIds`, `toggleTx`), added 'Owner participates' field display in the header card (SHARED only), added a 'Linked Transactions' card section showing linked transactions with description, date, category badge, and amount, added a 'Link Transactions' button opening a multi-select dialog with checkboxes, and wired the dialog form to `?/linkTransactions`.

Fixed a pre-existing bug in `native-date-picker.svelte` where the Calendar component was missing the required `type="single"` prop (causing 2 svelte-check errors that blocked the threshold-error check).

## Verification

Ran `cd frontend && npx svelte-check --threshold error`: 0 errors, 10 warnings (all pre-existing). Ran `cd frontend && npm run build`: completed successfully with only pre-existing circular dependency warnings from node_modules (not errors). All must-haves implemented: ownerParticipates toggle visible only for SHARED in the form dialog; Generate Billing button triggers POST and shows result count in toast; Linked Transactions section renders on subscription detail page; Link Transactions multi-select dialog implemented end-to-end.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && npx svelte-check --threshold error` | 0 | pass | 28000ms |
| 2 | `cd frontend && npm run build` | 0 | pass | 45000ms |

## Deviations

Fixed a pre-existing svelte-check error in `native-date-picker.svelte` (missing `type=\"single\"` on Calendar and implicit `any` parameter) that was not part of the task plan but was required for the verification gate to pass.

## Known Issues

none

## Files Created/Modified

- `frontend/src/routes/subscriptions/+page.server.ts`
- `frontend/src/routes/subscriptions/+page.svelte`
- `frontend/src/routes/subscriptions/[id]/+page.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.svelte`
- `frontend/src/lib/components/native-date-picker/native-date-picker.svelte`
