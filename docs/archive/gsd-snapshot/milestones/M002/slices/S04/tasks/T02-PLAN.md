---
estimated_steps: 3
estimated_files: 2
skills_used:
  - svelte-code-writer
---

# T02: Create transaction detail view with edit and delete actions

**Slice:** S04 — Mobile Card Layouts
**Milestone:** M002

## Description

Mobile card tap navigates to /transactions/[id] — this route does not exist yet. Need a detail page that loads a single transaction and provides edit/delete actions. Backend GET /api/transactions/{id} already exists.

## Steps

1. Create frontend/src/routes/transactions/[id]/+page.server.ts: fetch single transaction from GET /api/transactions/{id}, also load categories and contacts for edit form. Add update and delete form actions (same pattern as subscriptions/[id]/+page.server.ts). Use superValidate with the transaction schema.
2. Create frontend/src/routes/transactions/[id]/+page.svelte: display transaction details (amount colored by direction, description, date, CategoryBadge, contact) in a Card layout. Include Edit button (opens dialog with form) and Delete button (confirmation dialog). Add a back link to /transactions. Follow the pattern from debts/[id]/+page.svelte.
3. Reuse the transactionSchema pattern from the list page (redefine in detail page — follows existing debt/subscription pattern).

## Must-Haves

- [ ] /transactions/[id] route loads a single transaction
- [ ] Edit form submits to ?/update action
- [ ] Delete submits to ?/delete action
- [ ] Back link returns to /transactions

## Verification

- `cd frontend && npx svelte-check --threshold error` passes with no new errors from this route

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/routes/transactions/+page.svelte` — existing list page with transaction schema pattern
- `frontend/src/routes/subscriptions/[id]/+page.server.ts` — pattern reference for server load/actions
- `frontend/src/routes/debts/[id]/+page.svelte` — pattern reference for detail page layout

## Expected Output

- `frontend/src/routes/transactions/[id]/+page.server.ts` — server load and form actions for transaction detail
- `frontend/src/routes/transactions/[id]/+page.svelte` — transaction detail page with edit/delete UI
