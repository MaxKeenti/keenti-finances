---
estimated_steps: 23
estimated_files: 3
skills_used: []
---

# T03: Build SvelteKit /subscriptions/[id] detail page with payment recording

## Description

Create the subscription detail page that shows full subscription info, its members (for shared), and all payment records grouped by billing period. Users can record payments (mark PENDING → PAID) per member per period.

## Steps

1. Create `frontend/src/routes/subscriptions/[id]/+page.server.ts`:
   - Load function: fetch /api/subscriptions/{id} for subscription details, /api/subscriptions/{id}/members for member list, /api/subscriptions/{id}/payments for payment records
   - Action: recordPayment (PUT /api/subscriptions/{id}/payments/{paymentId}) to mark a payment as PAID
2. Create `frontend/src/routes/subscriptions/[id]/+page.svelte`:
   - Header section: subscription name, cost (MXN formatted), billing cycle badge, type badge, next billing date, category name if set
   - For SHARED: show token_uuid with a copy-to-clipboard button (this is the public link token for S07)
   - Members section (SHARED only): list members with name, share amount
   - Payment records section: group by billing_date, show each record with member name (or 'Owner' for personal), amount, status badge (PENDING in yellow, PAID in green), paid_date if paid
   - For PENDING records: show 'Record Payment' button that calls the recordPayment action
   - Back link to /subscriptions
   - Toast notifications for payment recording success/error
3. Link from the /subscriptions list page: each subscription card/row should link to /subscriptions/{id}

## Must-Haves

- Payment recording uses form action with enhance (not client-side fetch)
- Status badges visually distinguish PENDING (yellow/amber) vs PAID (green)
- Token UUID displayed for shared subscriptions with copy button
- Records grouped by billing period for readability
- Responsive layout at 390px and 1440px

## Verification

`bun run check` exits 0 AND `test -f frontend/src/routes/subscriptions/\[id\]/+page.svelte` AND `test -f frontend/src/routes/subscriptions/\[id\]/+page.server.ts`

## Inputs

- `frontend/src/routes/subscriptions/+page.svelte — add link to detail page from subscription list`
- `frontend/src/routes/subscriptions/+page.server.ts — reference for API base URL and fetch pattern`
- `frontend/src/routes/transactions/+page.svelte — reference for page layout and badge styling`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java — response shape with tokenUuid`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java — member response shape`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java — payment record response shape`

## Expected Output

- `frontend/src/routes/subscriptions/[id]/+page.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.svelte`

## Verification

bun run check && test -f 'frontend/src/routes/subscriptions/[id]/+page.svelte' && test -f 'frontend/src/routes/subscriptions/[id]/+page.server.ts'
