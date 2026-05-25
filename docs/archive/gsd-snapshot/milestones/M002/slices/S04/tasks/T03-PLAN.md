---
estimated_steps: 3
estimated_files: 2
skills_used:
  - svelte-code-writer
---

# T03: Polish subscriptions and debts card tap targets and verify mobile rendering

**Slice:** S04 — Mobile Card Layouts
**Milestone:** M002

## Description

Subscriptions and debts already use card layouts, but need full-card clickable area linking to detail view — not just title or View button. Ensures tap anywhere on card navigates to detail.

## Steps

1. In frontend/src/routes/subscriptions/+page.svelte: wrap each Card.Root content in an <a> tag linking to /subscriptions/{sub.id} or make the card a clickable container with goto(). Ensure entire card surface is tappable while action buttons still work via event.stopPropagation().
2. In frontend/src/routes/debts/+page.svelte: same treatment — full-card tap target links to /debts/{debt.id}.
3. Verify grid collapses to single column at mobile widths (existing grid gap-4 sm:grid-cols-2 pattern should already handle this).

## Must-Haves

- [ ] Tapping anywhere on a subscription card navigates to its detail view
- [ ] Tapping anywhere on a debt card navigates to its detail view
- [ ] Action buttons still work independently (stopPropagation)
- [ ] Grid collapses to single column at <=640px

## Verification

- `cd frontend && npx vite build` exits 0

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/routes/subscriptions/+page.svelte` — existing subscriptions list with card layout
- `frontend/src/routes/debts/+page.svelte` — existing debts list with card layout

## Expected Output

- `frontend/src/routes/subscriptions/+page.svelte` — full-card tap targets with stopPropagation on action buttons
- `frontend/src/routes/debts/+page.svelte` — full-card tap targets with stopPropagation on action buttons
