# S06: Debt Tracking — UAT

**Milestone:** M001
**Written:** 2026-05-14T15:44:37.022Z

# S06 UAT — Debt Tracking

**UAT Type:** Integration (backend + frontend wired end-to-end)

## Preconditions

- App running locally (Quarkus backend + SvelteKit dev server)
- User authenticated (session cookie present)
- At least one Contact exists (from S02 — required for debtor assignment)
- At least one INGRESS Category exists (from S02 — required for payment categorization)

---

## Scenario 1: Create an embroidery job debt

1. Navigate to `/debts`
2. Click **New Debt** (or equivalent create button)
3. Fill in: Debtor = existing contact, Description = "Bordado manteles x4", Total Amount = 800
4. Submit the form
5. **Expected:** New debt card appears showing debtor name, total MXN 800.00, paid MXN 0.00, remaining MXN 800.00, status badge PENDING

---

## Scenario 2: Edit a debt

1. On `/debts`, open the edit dialog for the debt created in Scenario 1
2. Change Total Amount to 900
3. Submit
4. **Expected:** Debt card updates to show total MXN 900.00, remaining MXN 900.00

---

## Scenario 3: View debt detail page

1. Click on the debt card (or navigate to `/debts/{id}`)
2. **Expected:** Detail page shows debt header (debtor, description, total/paid/remaining), progress bar at 0%, empty payment history table, and a "Record Payment" form pre-filled with remaining balance (900)

---

## Scenario 4: Record a partial payment

1. On `/debts/{id}`, enter: Amount = 400, Category = any INGRESS category, Notes = "Anticipo"
2. Submit the payment form
3. **Expected:**
   - Payment appears in payment history table (amount MXN 400, date, notes)
   - Progress bar advances to ~44%
   - Remaining balance updates to MXN 500
   - Debt status remains PENDING

---

## Scenario 5: Auto-INGRESS transaction creation

1. After recording the payment in Scenario 4, navigate to `/transactions`
2. **Expected:** A new INGRESS transaction appears with amount MXN 400, the INGRESS category selected in Scenario 4, and a description referencing the debt

---

## Scenario 6: Record full remaining payment → auto-PAID transition

1. Return to `/debts/{id}`
2. Record a second payment of MXN 500 (pre-filled remaining amount)
3. **Expected:**
   - Debt status transitions to PAID
   - Progress bar shows 100%
   - Remaining balance shows MXN 0.00
   - Second INGRESS transaction appears in `/transactions`

---

## Scenario 7: Dashboard reflects auto-ingress transactions

1. Navigate to `/` (dashboard)
2. **Expected:** Net balance and monthly income chart reflect the two INGRESS transactions (MXN 400 + MXN 500) created by the debt payments

---

## Scenario 8: Delete a debt

1. On `/debts`, create a second debt and delete it
2. **Expected:** Debt card disappears from list; previously recorded payments for the deleted debt are gone

---

## Edge Cases

- **Submitting with no debtor selected:** Validation error shown — contactId default 0 fails Zod .positive() check
- **Submitting with amount = 0:** Validation error shown — totalAmount must be positive
- **Navigate to `/debts/99999` (non-existent):** Backend returns 404 JSON; SvelteKit shows error state (no unhandled crash)

---

## Not Proven By This UAT

- Multi-user isolation (app is single-user)
- Flyway migration idempotency under concurrent startup (requires production Railway deployment — S08)
- Dashboard chart rendering on mobile Safari (S08 acceptance test)
- Payment recording when no INGRESS category exists (not a valid state if S02 is complete)

