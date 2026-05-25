# S04: S04

**Goal:** Transactions render as responsive cards on mobile (≤768px) with category badges and tap-to-detail navigation. Subscriptions and debts card tap targets verified. Transaction detail view created with edit/delete actions.
**Demo:** Transactions, subscriptions, and debts render as cards on mobile (≤768px) with category badges; tap navigates to detail view with action buttons

## Must-Haves

- Transaction cards visible at ≤768px with amount, description, date, CategoryBadge, contact; table visible at >768px; /transactions/[id] detail view loads single transaction with edit/delete; subscriptions and debts cards link correctly to their detail views; vite build and svelte-check pass.

## Proof Level

- This slice proves: contract

## Integration Closure

Consumes: app-shell layout from S01 (full-width main content area), CategoryBadge from S02 (OKLCH pill), Card components from shadcn-svelte. New wiring: /transactions/[id] route with server loader hitting GET /api/transactions/{id}. What remains: S07 browser-tests runtime verification.

## Verification

- Run the task and slice verification checks for this slice.

## Tasks

- [x] **T01: Added responsive mobile card grid to transactions list; table remains at md+ breakpoint, cards with CategoryBadge and tap-to-detail links render below md.** `est:45m`
  Why: Transactions page currently uses a table that is unusable on mobile. Need to render cards at <md breakpoint showing amount (colored by direction), description, date, CategoryBadge, and contact — each card links to /transactions/[id]. Table remains visible at md+ breakpoint for desktop users.
  - Files: `frontend/src/routes/transactions/+page.svelte`
  - Verify: cd frontend && npx vite build

- [x] **T02: Create transaction detail view with edit and delete actions** `est:1h`
  Why: Mobile card tap navigates to /transactions/[id] — this route does not exist yet. Need a detail page that loads a single transaction and provides edit/delete actions. Backend GET /api/transactions/{id} already exists.
  - Files: `frontend/src/routes/transactions/[id]/+page.server.ts`, `frontend/src/routes/transactions/[id]/+page.svelte`
  - Verify: cd frontend && npx svelte-check --threshold error

- [x] **T03: Polish subscriptions and debts card tap targets and verify mobile rendering** `est:30m`
  Why: Subscriptions and debts already use card layouts, but need full-card clickable area linking to detail view — not just title or View button. Ensures tap anywhere on card navigates to detail.
  - Files: `frontend/src/routes/subscriptions/+page.svelte`, `frontend/src/routes/debts/+page.svelte`
  - Verify: cd frontend && npx vite build

- [x] **T04: Final build and type-check verification** `est:15m`
  Why: All changes from T01-T03 must compile cleanly. svelte-check and vite build must pass with no new errors introduced by this slice.
  - Files: `frontend/src/routes/transactions/+page.svelte`, `frontend/src/routes/transactions/[id]/+page.svelte`, `frontend/src/routes/transactions/[id]/+page.server.ts`, `frontend/src/routes/subscriptions/+page.svelte`, `frontend/src/routes/debts/+page.svelte`
  - Verify: cd frontend && npx svelte-check --threshold error

## Files Likely Touched

- frontend/src/routes/transactions/+page.svelte
- frontend/src/routes/transactions/[id]/+page.server.ts
- frontend/src/routes/transactions/[id]/+page.svelte
- frontend/src/routes/subscriptions/+page.svelte
- frontend/src/routes/debts/+page.svelte
