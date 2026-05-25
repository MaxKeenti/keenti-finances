# S04: Mobile Card Layouts & Transaction Detail View — UAT

**Milestone:** M002
**Written:** 2026-05-17T17:31:49.609Z

# S04: Mobile Card Layouts & Transaction Detail View — UAT

**Milestone:** M002
**Written:** 2026-05-17

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: All deliverables are SvelteKit route files and Svelte components. Build and type-check pass proves the code compiles and types correctly. Runtime browser verification of tap behavior and API integration is deferred to S07 (Railway deployment and production verification).

## Preconditions

- Dev server running: `cd frontend && bun run dev`
- Backend API running with seeded transaction, subscription, and debt data
- Browser viewport set to ≤768px (mobile emulation) and >768px (desktop) for responsive checks

## Smoke Test

Navigate to `/transactions` at ≤768px — card grid should be visible with no horizontal scrollbar. Tap a card — browser should navigate to `/transactions/[id]`.

## Test Cases

### 1. Transaction list renders cards on mobile

1. Open `/transactions` with viewport ≤768px
2. Confirm card grid is visible (amount, description, date, category badge, contact)
3. Confirm no table is visible at this viewport
4. **Expected:** Card grid shown; table hidden; each card is a full-surface tap target

### 2. Transaction list renders table on desktop

1. Open `/transactions` with viewport >768px
2. **Expected:** Table visible; card grid hidden; columns include amount, description, date, category, contact

### 3. Transaction card tap navigates to detail view

1. On mobile viewport, tap any transaction card
2. **Expected:** Browser navigates to `/transactions/[id]`; detail view shows transaction fields with Edit and Delete buttons

### 4. Transaction detail — edit action

1. On `/transactions/[id]`, click Edit
2. Modify a field (e.g., description)
3. Submit the edit dialog
4. **Expected:** Form submits; page reflects updated data or redirects correctly

### 5. Transaction detail — delete action

1. On `/transactions/[id]`, click Delete
2. **Expected:** Transaction deleted; browser redirects to `/transactions` list

### 6. Subscription card — full-surface tap

1. Open `/subscriptions` at any viewport
2. Tap anywhere on a subscription card (not on View/Payments button)
3. **Expected:** Browser navigates to the subscription detail view

### 7. Subscription card — action buttons independently clickable

1. On a subscription card, click the View or Payments button specifically
2. **Expected:** Button action triggers correctly; stretched-link does not interfere

### 8. Debt card — full-surface tap

1. Open `/debts` at any viewport
2. Tap anywhere on a debt card (not on the action button)
3. **Expected:** Browser navigates to the debt detail view

## Edge Cases

### Transaction with no category

1. Navigate to a transaction that has no category assigned
2. Open `/transactions` on mobile
3. **Expected:** Card renders without CategoryBadge; no layout breakage

### Transaction detail for non-existent ID

1. Navigate to `/transactions/99999999` (invalid ID)
2. **Expected:** Page handles 404 gracefully (error page or redirect)

## Failure Signals

- Card grid not visible at ≤768px — responsive classes not applied
- Table visible at ≤768px — breakpoint toggle broken
- `/transactions/[id]` returns 404 or blank — route not registered
- Edit/delete actions throw 500 — server loader or action not wired correctly
- Subscription/debt card tap does nothing — stretched-link CSS not applied

## Not Proven By This UAT

- Actual device touch interaction (tested only via browser emulation or keyboard)
- Live API round-trip for edit and delete on a deployed instance (deferred to S07)
- Category badge hue correctness (proven by S02)
- Auth-gated access to transaction detail (deferred to S05/S07)

## Notes for Tester

- The stretched-link pattern on subscription/debt cards uses `position: relative` on the card and `position: absolute; inset: 0` on the link. Inspect element if tap isn't working — check z-index stacking of action buttons vs the stretched link.
- The transaction detail page edit dialog was built following the same pattern as debt and subscription detail pages — check those as reference if the form behaves unexpectedly.
- 14 pre-existing svelte-check errors from `node_modules/effect` and the `native-date-picker` component are unrelated to S04 and can be ignored.
