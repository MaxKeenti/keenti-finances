---
estimated_steps: 8
estimated_files: 3
skills_used: []
---

# T04: Display category badges in categories list and transactions table

Closes the visual loop — badges must appear wherever categories are shown so users see the color coding in context.

Do:
1. In frontend/src/routes/categories/+page.svelte: import CategoryBadge; in the categories list/table, render CategoryBadge with each category's color and name instead of plain text
2. In frontend/src/routes/transactions/+page.svelte: import CategoryBadge; replace the plain text {tx.categoryName ?? '—'} with a CategoryBadge using tx.categoryColor and tx.categoryName
3. Update the transaction type definition in +page.svelte or +page.server.ts to include categoryColor field
4. Update frontend/src/routes/categories/+page.server.ts Category type to include color field in the categories array type
5. Run npx svelte-check --threshold error to confirm no new errors in S02 files

Done when: Categories page shows colored badges next to names; transactions page shows category badge instead of plain text; vite build exits 0; svelte-check reports 0 new errors in S02 files.

## Inputs

- `frontend/src/lib/components/ui/category-badge/index.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/transactions/+page.svelte`

## Expected Output

- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/src/routes/transactions/+page.server.ts`

## Verification

npx svelte-check --threshold error --workspace frontend
