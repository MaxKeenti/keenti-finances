# S05: Subscription Management — UAT

**Milestone:** M001
**Written:** 2026-05-14T13:09:27.653Z

# S05 UAT — Subscription Management

## UAT Type
Integration — backend REST contract + SvelteKit UI flow

## Preconditions
- App running locally (Quarkus dev mode + SvelteKit dev server)
- User authenticated (from S01)
- At least 2 contacts exist (from S02, for member assignment)
- Database seeded with Flyway V4 migration applied

---

## Scenario 1: Create a PERSONAL subscription

1. Navigate to `/subscriptions`
2. Click "New Subscription"
3. Fill: Name = "Netflix", Amount = 250, Billing Day = 15, Type = PERSONAL
4. Submit

**Expected:** Card appears in grid showing "Netflix / $250 / Personal". No members panel shown. No UUID token.

---

## Scenario 2: Create a SHARED subscription with members

1. Click "New Subscription"
2. Fill: Name = "Gym", Amount = 500, Billing Day = 1, Type = SHARED
3. Submit
4. On the Gym card, click "Manage Members" → select contact from dropdown → click "Add"

**Expected:** Member count increments on card. Navigation to `/subscriptions/[id]` shows member in list. UUID token displayed with copy button. Duplicate add attempt returns an error toast (409).

---

## Scenario 3: Edit a subscription — type change SHARED→PERSONAL

1. Open a SHARED subscription edit dialog
2. Change Type to PERSONAL → submit

**Expected:** Token UUID is cleared (not shown in detail page). Member management panel hidden.

---

## Scenario 4: Scheduler generates PENDING payment records

1. Ensure a subscription has `next_billing_date` within 7 days from today
2. Trigger scheduler manually (or wait for 1am cron), OR simulate by calling the scheduler endpoint in dev mode
3. Navigate to `/subscriptions/[id]`

**Expected:** A PENDING payment record appears under the upcoming billing date. If subscription is SHARED, one PENDING record per member is shown.

---

## Scenario 5: Record a payment

1. On `/subscriptions/[id]`, locate a PENDING payment record
2. Click "Mark Paid" for a member

**Expected:** Row status changes from PENDING to PAID. Success toast shown. Refreshing the page preserves PAID status.

---

## Scenario 6: Delete a subscription

1. On `/subscriptions`, open a subscription card menu → click "Delete"
2. Confirm deletion

**Expected:** Card removed from grid. Navigating to the old `/subscriptions/[id]` returns 404 or redirects.

---

## Edge Cases

| Case | Expected |
|---|---|
| Add member to PERSONAL subscription | 400 error toast — "Cannot add members to a personal subscription" |
| Add same contact twice to SHARED | 409 error toast — duplicate member rejected |
| Access `/subscriptions/[id]` for nonexistent ID | 404 response from backend, SvelteKit surfaces error |
| No upcoming billing within 7 days | Scheduler runs and logs 0 records generated; no new payment rows |

---

## Not Proven By This UAT

- Scheduler runs automatically at 1am (cron trigger not testable without time manipulation)
- Public token link (UUID-based read-only view) — covered in S07
- Production database behavior under Railway deployment — covered in S08
