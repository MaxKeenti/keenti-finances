# Quick Task: It seems that our current implementation on categories as a category marked as "both" (ingress/egress) is not being displayed when the select shows ingress only categories

**Date:** 2026-05-15
**Branch:** gsd/quick/3-it-seems-that-our-current-implementation

## What Changed
- Fixed `ingressCategories` filter in debt payment page to include `BOTH` categories (was `=== 'INGRESS'` only)
- Added `filteredCategories` derived in transactions page that filters by the current direction and includes `BOTH`
- Added `$effect` in transactions page to reset `categoryId` when direction changes and the selected category no longer applies

## Files Modified
- `frontend/src/routes/debts/[id]/+page.svelte` — filter fix: `c.type === 'INGRESS' || c.type === 'BOTH'`
- `frontend/src/routes/transactions/+page.svelte` — direction-aware category filtering with auto-reset on direction change

## Verification
- Type check passes with no new errors introduced by the changes
- Pre-existing Calendar/effect errors in subscriptions and transactions are unrelated to this fix
