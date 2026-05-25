---
estimated_steps: 29
estimated_files: 2
skills_used: []
---

# T02: SvelteKit /transactions CRUD page with category and contact selectors

Build the SvelteKit /transactions route following the established CRUD page pattern from S02: single Zod schema for create/update, superforms for validation, plain enhance for delete, sonner toast notifications, shadcn-svelte Dialog for edit/delete, Table for listing. Transaction form includes: amount (number input), direction (select: INGRESS/EGRESS), description (text input), transaction date (date input), category (select populated from /api/categories), and optional contact (select populated from /api/contacts).

## Steps

1. Create `frontend/src/routes/transactions/+page.server.ts`:
   - Define `transactionSchema` with Zod: id (coerce number optional), amount (coerce number positive), direction (enum INGRESS/EGRESS), description (string optional, max 500), transactionDate (string for date input), categoryId (coerce number), contactId (coerce number optional or empty string).
   - `load` function: fetch `/api/transactions`, `/api/categories`, `/api/contacts` from BACKEND (http://localhost:8080) in parallel. Return { transactions, categories, contacts, form }.
   - `create` action: superValidate request, POST to /api/transactions with JSON body mapping form fields. Handle 400/404/502 errors with toast messages. Coerce empty contactId to null.
   - `update` action: superValidate, PUT to /api/transactions/{id}. Handle 404/400/502.
   - `delete` action: plain formData, DELETE to /api/transactions/{id}. Handle 404/502.
2. Create `frontend/src/routes/transactions/+page.svelte`:
   - Follow categories page pattern exactly: superForm with zod4Client, Dialog for create/edit, Dialog for delete confirm.
   - Table columns: Date, Description, Amount (green for INGRESS, red for EGRESS, formatted as MXN currency), Category (name from joined data), Contact (name or '—'), Actions (Edit/Delete buttons).
   - Form fields: amount (Input type=number step=0.01 min=0.01), direction (select), description (Input), transactionDate (Input type=date), categoryId (select from categories list), contactId (select from contacts list with empty option).
   - Amount display: format with Intl.NumberFormat('es-MX', { style: 'currency', currency: 'MXN' }). Prefix with + for INGRESS, - for EGRESS.
   - Empty state: 'No transactions yet. Create one to get started.'
3. Verify: `bun run check` exits 0 with 0 errors. File existence checks pass.

## Must-Haves

- [ ] Single Zod schema for create and update with dynamic action switching
- [ ] Category and contact dropdowns populated from backend
- [ ] Amount formatted as MXN currency with direction-based color
- [ ] Delete uses plain formData + native enhance (no superforms)
- [ ] Empty contactId coerced to null before backend request
- [ ] Sonner toast notifications for success/failure
- [ ] Date input defaults to today for new transactions

## Verification

- `cd frontend && bun run check` exits 0 with 0 errors
- `test -f frontend/src/routes/transactions/+page.svelte && test -f frontend/src/routes/transactions/+page.server.ts`

## Skills Used

- svelte-code-writer
- svelte-core-bestpractices

## Inputs

- `frontend/src/routes/categories/+page.server.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/contacts/+page.server.ts`
- `frontend/src/routes/contacts/+page.svelte`
- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`

## Expected Output

- `frontend/src/routes/transactions/+page.server.ts`
- `frontend/src/routes/transactions/+page.svelte`

## Verification

cd /Users/moonstone/Source/Personal/keenti-finances/.gsd/worktrees/M001/frontend && bun run check && test -f src/routes/transactions/+page.svelte && test -f src/routes/transactions/+page.server.ts
