---
estimated_steps: 31
estimated_files: 2
skills_used: []
---

# T03: Build SvelteKit /debts/[id] detail page with payment history and partial payment recording

## Description

Build the SvelteKit /debts/[id] detail route showing full debt information, payment history table, and a form to record partial/full payments. When a payment is recorded, the backend auto-creates an INGRESS transaction. The page shows the remaining balance updating after each payment.

## Steps

1. Create `frontend/src/routes/debts/[id]/+page.server.ts`:
   - Load function: fetch /api/debts/{id} for debt details, /api/debts/{id}/payments for payment history, /api/categories for the payment category selector
   - Define Zod schema for payment: amount (required, positive), paymentDate (required), categoryId (required — category for the auto-ingress), notes (optional)
   - Action: recordPayment (POST /api/debts/{id}/payments) via superforms
   - Follow the subscription detail page pattern from S05

2. Create `frontend/src/routes/debts/[id]/+page.svelte`:
   - Header section: debtor name, description, total amount, status badge
   - Progress indicator: visual bar or text showing paid/total with percentage
   - Payment history table (shadcn Table): date, amount (MXN), notes, linked transaction id
   - Record payment form: amount input (pre-filled with remaining balance), date picker, category selector (for the ingress transaction category), notes textarea
   - Amount validation: cannot exceed remaining balance (client-side + server-side)
   - After successful payment: invalidateAll to refresh data, show toast
   - Back link to /debts
   - Disable payment form when debt status is PAID

## Must-Haves

- [ ] Payment form validates amount does not exceed remaining balance
- [ ] Payment history table shows all payments with MXN formatting
- [ ] Category selector present for ingress transaction categorization
- [ ] Debt status and remaining balance visible
- [ ] Form disabled when debt is fully PAID
- [ ] bun run check exits 0

## Verification

- `cd frontend && bun run check` exits 0
- `test -f frontend/src/routes/debts/\[id\]/+page.svelte`
- `test -f frontend/src/routes/debts/\[id\]/+page.server.ts`

## Skills Used

- svelte-code-writer
- svelte-core-bestpractices

## Inputs

- `frontend/src/routes/subscriptions/[id]/+page.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.svelte`
- `frontend/src/routes/debts/+page.server.ts`
- `frontend/src/routes/debts/+page.svelte`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentRequest.java`

## Expected Output

- `frontend/src/routes/debts/[id]/+page.server.ts`
- `frontend/src/routes/debts/[id]/+page.svelte`

## Verification

cd frontend && bun run check && test -f src/routes/debts/\[id\]/+page.svelte && test -f src/routes/debts/\[id\]/+page.server.ts
