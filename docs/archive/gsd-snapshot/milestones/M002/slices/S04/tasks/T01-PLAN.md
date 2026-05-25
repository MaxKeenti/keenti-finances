---
estimated_steps: 6
estimated_files: 1
skills_used:
  - svelte-code-writer
---

# T01: Add mobile card view to transactions list with responsive breakpoint toggle

**Slice:** S04 — Mobile Card Layouts
**Milestone:** M002

## Description

Transactions page currently uses a table that is unusable on mobile. Need to render cards at <md breakpoint showing amount (colored by direction), description, date, CategoryBadge, and contact — each card links to /transactions/[id]. Table remains visible at md+ breakpoint for desktop users.

## Steps

1. In /transactions/+page.svelte, wrap existing table in a div with class "hidden md:block"
2. Add a new div with class "md:hidden" containing a responsive card grid (grid gap-4)
3. Each card uses Card.Root > Card.Content with: amount (font-mono, colored green/red by direction), description, transactionDate, CategoryBadge (hue, name, direction), contactName
4. Wrap each card in an `<a href="/transactions/{tx.id}">` for tap navigation
5. Import Card components from $lib/components/ui/card
6. Keep formatAmount helper for consistent formatting in both views

## Must-Haves

- [ ] At <768px, transaction cards render in a grid
- [ ] At >=768px, the existing table renders
- [ ] Cards show amount, description, date, CategoryBadge, and contact
- [ ] Each card links to /transactions/{id}

## Verification

- `cd frontend && npx vite build` exits 0
- Visual: at mobile viewport, cards display instead of table

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/routes/transactions/+page.svelte` — existing transactions list page with table layout

## Expected Output

- `frontend/src/routes/transactions/+page.svelte` — modified with responsive card/table toggle at md breakpoint
