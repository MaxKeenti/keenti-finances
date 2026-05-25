---
estimated_steps: 33
estimated_files: 2
skills_used: []
---

# T02: Build SvelteKit /debts CRUD page with debt cards, remaining balance, and create/edit dialogs

## Description

Build the SvelteKit /debts route following the established CRUD pattern from S02/S03/S05. Page shows a card grid of debts grouped or listed with debtor name, description, total amount, total paid, remaining balance, and status badge (ACTIVE/PAID). Create and edit debts via shadcn Dialog with contact selector and amount input. Delete with confirmation dialog.

## Steps

1. Create `frontend/src/routes/debts/+page.server.ts`:
   - Define Zod schema for debt: contactId (required), description (required), totalAmount (required, positive)
   - Load function: fetch /api/debts, /api/contacts, /api/categories from backend via proxy
   - Actions: create (POST /api/debts), update (PUT /api/debts/{id}), delete (DELETE /api/debts/{id})
   - Follow superforms + zod4Client pattern from transactions page

2. Create `frontend/src/routes/debts/+page.svelte`:
   - Card grid layout (like subscriptions page)
   - Each card shows: debtor name, description, total amount (MXN), paid amount, remaining balance, status badge
   - Remaining = totalAmount - totalPaid (computed from response fields)
   - Color coding: green remaining for PAID debts, amber/red for ACTIVE
   - Create button opens Dialog with contact selector dropdown, description textarea, total amount input
   - Edit button on each card opens pre-filled Dialog
   - Delete button with confirmation Dialog
   - Each card links to /debts/[id] for payment details
   - Toast notifications via sonner for success/failure
   - MXN formatting via Intl.NumberFormat('es-MX', MXN) matching transactions page pattern

## Must-Haves

- [ ] Zod schema validates contactId as required, totalAmount as positive
- [ ] Card shows debtor name, total, paid, remaining with MXN formatting
- [ ] Status badge distinguishes ACTIVE vs PAID
- [ ] Create/edit/delete actions work through superforms
- [ ] Each debt card links to /debts/[id]
- [ ] bun run check exits 0

## Verification

- `cd frontend && bun run check` exits 0
- `test -f frontend/src/routes/debts/+page.svelte`
- `test -f frontend/src/routes/debts/+page.server.ts`

## Skills Used

- svelte-code-writer
- svelte-core-bestpractices

## Inputs

- `frontend/src/routes/subscriptions/+page.server.ts`
- `frontend/src/routes/subscriptions/+page.svelte`
- `frontend/src/routes/transactions/+page.server.ts`
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/src/routes/api/[...path]/+server.ts`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtRequest.java`

## Expected Output

- `frontend/src/routes/debts/+page.server.ts`
- `frontend/src/routes/debts/+page.svelte`

## Verification

cd frontend && bun run check && test -f src/routes/debts/+page.svelte && test -f src/routes/debts/+page.server.ts
