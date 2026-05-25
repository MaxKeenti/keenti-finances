# S03: S03 — UAT

**Milestone:** M002
**Written:** 2026-05-17T12:20:17.420Z

# S03 UAT: Subscription Model Improvements

**UAT Type:** Manual functional verification against a running backend + frontend dev server

**Preconditions:**
- Backend running with Flyway migrations applied (V8 migration executed — `owner_participates` and `subscription_id` columns exist)
- At least one SHARED subscription exists with ≥2 members
- At least one PERSONAL subscription exists
- At least 3 unlinked transactions exist in the system

---

## Scenario 1: Owner Participation Toggle (SHARED subscription)

1. Navigate to a SHARED subscription detail page
2. Observe: header shows an `ownerParticipates` badge
3. Observe: a toggle/checkbox for owner participation is visible in the page
4. Note each member's current share amount
5. Toggle `ownerParticipates` to OFF (owner excluded from split) and save
6. **Expected:** Each member's share increases (cost divided among members only, not owner)
7. Toggle `ownerParticipates` back to ON and save
8. **Expected:** Each member's share returns to original value (cost divided among members + owner)

**Edge case:** Navigate to a PERSONAL subscription detail page — owner participation toggle must NOT be visible.

---

## Scenario 2: Generate Billing Button

1. Navigate to the Subscriptions list page
2. Locate the "Generate Billing" button
3. Click "Generate Billing"
4. **Expected:** A toast notification appears showing the number of payment records created (e.g., "Generated 3 billing records")
5. Click "Generate Billing" a second time immediately
6. **Expected:** Toast shows 0 records created (idempotent — records already exist for this period)

---

## Scenario 3: Transaction Linking — Link dialog

1. Navigate to a SHARED or PERSONAL subscription detail page
2. Scroll to the "Linked Transactions" section
3. Observe: section is empty (or shows currently linked transactions)
4. Click "Link Transactions"
5. **Expected:** A multi-select dialog opens listing unlinked transactions with inline previews: amount, date, description, and category badge
6. Select 2–3 transactions and confirm
7. **Expected:** Dialog closes; "Linked Transactions" section now shows the selected transactions with their inline previews

---

## Scenario 4: Transaction Linking — Inline preview on detail page

1. After linking transactions (Scenario 3), stay on the subscription detail page
2. Observe each linked transaction row shows: category badge (correct hue/color), formatted amount, date, and description
3. **Expected:** All fields render without layout breakage

---

## Scenario 5: Unlinking a transaction (API-level)

1. Note a linked transaction's ID from the detail page
2. Call `PUT /api/transactions/{id}/link-subscription` with body `{"subscriptionId": null}`
3. **Expected:** 200 response; transaction no longer appears in `GET /api/subscriptions/{id}/linked-transactions`

---

## Not Proven By This UAT

- Correctness of billing amount calculations in generated payment records (requires S06 JUnit tests)
- Behavior under concurrent requests to generate-billing
- Mobile layout of the linked transactions section (covered in S04)
- Production Railway deployment (covered in S07)

