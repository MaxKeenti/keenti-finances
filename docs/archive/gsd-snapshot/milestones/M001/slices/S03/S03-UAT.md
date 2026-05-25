# S03: Transaction Tracking — UAT

**Milestone:** M001
**Written:** 2026-05-14T01:45:58.750Z

# S03: Transaction Tracking — UAT

**Milestone:** M001
**Written:** 2026-05-13

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: All verification checks pass at the build and type-check level. Live-runtime proof (actual DB round-trip) requires a running PostgreSQL + Quarkus + SvelteKit stack, deferred to integration/deployment in S08. The artifact-driven checks confirm the full stack is wired and type-safe.

## Preconditions

- PostgreSQL running with `keenti_finances` database
- Quarkus backend started (`./mvnw quarkus:dev`) — Flyway V3 runs automatically on startup, creating the `transaction` table
- SvelteKit frontend started (`bun run dev`) at http://localhost:5173
- User logged in (session cookie present)
- At least one category exists (created via /categories)

## Smoke Test

Navigate to http://localhost:5173/transactions. The page loads with an empty table and a visible "Add Transaction" button. No console errors.

## Test Cases

### 1. Create an ingress transaction

1. Click "Add Transaction"
2. Fill in: Amount = `1500`, Direction = `INGRESS`, Description = `Salary`, Date = today, Category = any existing category
3. Leave Contact blank
4. Click Save
5. **Expected:** Toast "Transaction created"; new row appears in table showing `+$1,500.00 MXN` in green, the description, and category name

### 2. Create an egress transaction

1. Click "Add Transaction"
2. Fill in: Amount = `350.50`, Direction = `EGRESS`, Description = `Groceries`, Date = today, Category = any existing category
3. Click Save
4. **Expected:** Toast "Transaction created"; row shows `-$350.50 MXN` in red

### 3. Edit a transaction

1. Click the edit icon/button on an existing row
2. Change the description to `Updated description`
3. Click Save
4. **Expected:** Toast "Transaction updated"; row reflects new description

### 4. Delete a transaction

1. Click the delete icon/button on an existing row
2. Confirm in the dialog
3. **Expected:** Toast "Transaction deleted"; row disappears from table

### 5. Category dropdown populated

1. Open "Add Transaction" form
2. **Expected:** Category select shows all categories created in /categories; no empty/broken option

### 6. Optional contact selector

1. Open "Add Transaction" form
2. **Expected:** Contact select has a blank/none option and lists all contacts from /contacts; selecting none is valid

### 7. Persist across sessions

1. Create a transaction, note its description
2. Reload the page (F5)
3. **Expected:** Transaction still appears — data is persisted to PostgreSQL via the backend

## Edge Cases

### Empty amount or zero

1. Open "Add Transaction", leave Amount blank or enter 0, click Save
2. **Expected:** Form validation error shown inline; no request sent to backend

### Missing category

1. Open "Add Transaction", fill all fields except Category, click Save
2. **Expected:** Form validation error on category field; transaction not created

### Backend 404 on edit of deleted record

1. Delete a transaction in one tab while the edit dialog is open in another
2. Submit the edit
3. **Expected:** Error toast surfaced (backend returns 404 JSON, SvelteKit surfaces it)

## Failure Signals

- Page fails to load or shows blank: SvelteKit server action error — check browser console and Quarkus logs
- Category/Contact selects are empty: proxy to /api/categories or /api/contacts failed — check Quarkus is running and CORS/proxy config
- Transaction not persisting after reload: Flyway V3 migration may not have run — check Quarkus startup logs for migration errors
- Amount shows `NaN` or unformatted: Intl.NumberFormat not supported (unlikely) or amount type coercion issue

## Not Proven By This UAT

- Live PostgreSQL round-trip (requires running stack — proven in S08 deployment)
- Quarkus structured log output format on CRUD operations
- Backend 400 error response body shape (no form submission of invalid data to backend tested here without running stack)
- Mobile Safari rendering of the transactions page (proven in S08)
- Performance under large transaction volume (no pagination implemented)

## Notes for Tester

- The 4 svelte-check warnings are pre-existing across categories/contacts/login pages (`state_referenced_locally`) and are not regressions from this slice
- Contact is optional — leaving the select blank is valid and should result in `contact_id = null` in the DB
- Direction colors: INGRESS = green (`text-green-*`), EGRESS = red (`text-red-*`)

