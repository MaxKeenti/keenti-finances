# Quick Task: Previsualization on selects show only the id, not the text the value that id stores, so its a bit hard to guess which one is actually selected. Also, the selects are not sorted, so sorting the options alphabetically is a better approach for visual searching a certain category

**Date:** 2026-05-15
**Branch:** gsd/quick/4-previsualization-on-selects-show-only-th

## What Changed
- Added `label={name}` prop to every `Select.Item` that uses a numeric ID as `value` — bits-ui uses this prop to populate the trigger's `Select.Value` preview; without it the raw ID string was shown instead of the human-readable name.
- Sorted category lists with `.sort((a, b) => a.name.localeCompare(b.name))` in `filteredCategories` (transactions) and `ingressCategories` (debt payments).
- Added `sortedContacts` derived in transactions page and applied the same sort to the contacts list in `debts/+page.svelte`.

## Files Modified
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/src/routes/debts/+page.svelte`
- `frontend/src/routes/debts/[id]/+page.svelte`

## Verification
- Type-check (`bun run check`) passes with no new errors (pre-existing errors in node_modules/effect and calendar components are unrelated).
- All three select contexts fixed: transaction category, transaction contact, debt contact (debtor), debt payment ingress category.
