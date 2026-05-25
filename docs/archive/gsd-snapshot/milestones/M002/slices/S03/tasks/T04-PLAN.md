---
estimated_steps: 8
estimated_files: 4
skills_used:
  - svelte-code-writer
---

# T04: Frontend owner participation toggle, generate billing button, and transaction linking UI

**Slice:** S03 — Subscription Model Improvements
**Milestone:** M002

## Description

Users need UI controls to toggle owner participation on SHARED subscriptions, trigger billing generation, and link transactions to subscriptions from the detail page.

## Steps

1. Add ownerParticipates field to subscription schema in +page.server.ts (Boolean, default true). Pass in create/update action bodies.
2. In +page.svelte subscription form dialog: add toggle/checkbox for ownerParticipates, visible only when type === 'SHARED'. Label: 'I participate in this subscription'.
3. Add 'Generate Billing' button on subscriptions list page. Wire to new generateBilling form action that POSTs to /api/subscriptions/generate-billing. Show success toast with count.
4. Add generateBilling action to subscriptions/+page.server.ts.
5. On subscription detail page ([id]/+page.server.ts): fetch linked transactions via GET /api/subscriptions/{id}/linked-transactions. Fetch unlinked transactions for the linking dialog.
6. On subscription detail page ([id]/+page.svelte): add 'Linked Transactions' section showing linked transactions with amount, date, description, category badge. Add 'Link Transactions' button opening multi-select dialog.
7. Wire confirm to POST action that calls PUT /api/transactions/{id}/link-subscription for each selected transaction.
8. Display ownerParticipates status on detail page header.

## Must-Haves

- [ ] ownerParticipates toggle visible only for SHARED subscriptions in form dialog
- [ ] Generate Billing button triggers POST and shows result count in toast
- [ ] Linked Transactions section renders on subscription detail page
- [ ] Link Transactions multi-select dialog works end-to-end
- [ ] svelte-check and vite build pass

## Verification

- `cd frontend && npx svelte-check --threshold error` passes
- `cd frontend && npm run build` succeeds

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/routes/subscriptions/+page.server.ts` — existing server load/actions
- `frontend/src/routes/subscriptions/+page.svelte` — existing subscription list page
- `frontend/src/routes/subscriptions/[id]/+page.server.ts` — existing detail page server
- `frontend/src/routes/subscriptions/[id]/+page.svelte` — existing detail page

## Expected Output

- `frontend/src/routes/subscriptions/+page.server.ts` — ownerParticipates in schema, generateBilling action added
- `frontend/src/routes/subscriptions/+page.svelte` — ownerParticipates toggle, Generate Billing button added
- `frontend/src/routes/subscriptions/[id]/+page.server.ts` — linked transactions loader, link action added
- `frontend/src/routes/subscriptions/[id]/+page.svelte` — Linked Transactions section, Link dialog, ownerParticipates display added
