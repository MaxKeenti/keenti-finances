---
estimated_steps: 4
estimated_files: 5
skills_used: []
---

# T04: Final build and type-check verification

**Slice:** S04 — Mobile Card Layouts
**Milestone:** M002

## Description

All changes from T01-T03 must compile cleanly. svelte-check and vite build must pass with no new errors introduced by this slice.

## Steps

1. Run bun install in frontend/ (worktree may need it)
2. Run npx vite build — must exit 0
3. Run npx svelte-check --threshold error — no new errors from S04 files
4. Verify /transactions/[id] route files exist

## Must-Haves

- [ ] vite build exits 0
- [ ] svelte-check reports no errors in S04-touched files (pre-existing errors acceptable)
- [ ] /transactions/[id] route files exist on disk

## Verification

- `cd frontend && npx svelte-check --threshold error` passes
- `cd frontend && npx vite build` exits 0

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/routes/transactions/+page.svelte` — modified in T01
- `frontend/src/routes/transactions/[id]/+page.svelte` — created in T02
- `frontend/src/routes/transactions/[id]/+page.server.ts` — created in T02
- `frontend/src/routes/subscriptions/+page.svelte` — modified in T03
- `frontend/src/routes/debts/+page.svelte` — modified in T03

## Expected Output

- `frontend/src/routes/transactions/+page.svelte` — verified clean build
- `frontend/src/routes/transactions/[id]/+page.svelte` — verified clean build
- `frontend/src/routes/transactions/[id]/+page.server.ts` — verified clean build
- `frontend/src/routes/subscriptions/+page.svelte` — verified clean build
- `frontend/src/routes/debts/+page.svelte` — verified clean build
