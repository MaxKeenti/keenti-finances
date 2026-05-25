---
estimated_steps: 39
estimated_files: 3
skills_used: []
---

# T02: Build SvelteKit dashboard page with Layerchart bar chart, trend line, and net balance card

---
estimated_steps: 9
estimated_files: 6
skills_used:
  - svelte-code-writer
  - svelte-core-bestpractices
---

# T02: Build SvelteKit dashboard page with Layerchart bar chart, trend line, and net balance card

**Slice:** S04 — Financial Dashboard
**Milestone:** M001

## Description

Replace the placeholder dashboard at `/` with a full financial dashboard. Install `layerchart` and `d3-scale` as dependencies. Create a `+page.server.ts` load function that fetches `/api/dashboard/summary?year=YYYY` from the Quarkus backend (using the same direct backend fetch pattern as transactions). Build the Svelte page with three sections: (1) net balance card showing all-time balance formatted in MXN, (2) monthly bar chart showing income (green) vs expenses (red) per month, (3) yearly trend line showing cumulative net per month. Include a year selector to switch between years. Handle empty state gracefully (no transactions = zero-value charts, $0.00 balance).

## Steps

1. Install dependencies: `cd frontend && bun add layerchart d3-scale`
2. Create `frontend/src/routes/+page.server.ts` — load function that fetches `http://localhost:8080/api/dashboard/summary?year=YYYY` (default: current year). Type the response. Return `{ summary, year }` to the page.
3. Replace `frontend/src/routes/+page.svelte` — remove placeholder card, build dashboard layout:
   - Top: Net balance card with MXN formatting (Intl.NumberFormat('es-MX', {style:'currency', currency:'MXN'}))
   - Below: Year selector (simple prev/next buttons or select) that triggers page reload with `?year=` query param
   - Middle: Monthly bar chart using Layerchart's `Chart`, `Bar`, `Axis` components — grouped bars for ingress (green) and egress (red), x-axis = month names, y-axis = MXN amounts
   - Bottom: Yearly trend line using Layerchart's `Line` component — monthly net (ingress - egress) as a line chart showing the trend
4. Style with Tailwind classes consistent with existing pages. Use shadcn Card components for chart containers.
5. Handle empty state: when all monthly values are zero, show the charts with zero baseline and a subtle "No transactions yet" message
6. Ensure year selector defaults to current year and allows navigating to previous years
7. Format all monetary values with MXN currency formatting matching the transactions page pattern
8. Run `bun run check` to verify no type errors
9. Verify the page renders correctly with the dev server

## Must-Haves

- [ ] layerchart and d3-scale installed as frontend dependencies
- [ ] Monthly bar chart with green (ingress) and red (egress) grouped bars
- [ ] Yearly trend line showing monthly net income
- [ ] Net balance card with MXN formatting
- [ ] Year selector for switching between years
- [ ] Empty state handled gracefully
- [ ] `bun run check` passes with no new errors

## Verification

- `cd frontend && bun run check` exits 0 with no new errors beyond pre-existing warnings
- `grep -q 'layerchart' frontend/package.json` exits 0
- `test -f frontend/src/routes/+page.server.ts && test -f frontend/src/routes/+page.svelte`
- Page loads at `http://localhost:5173/` showing dashboard with chart components

## Inputs

- `frontend/src/routes/+page.svelte`
- `frontend/src/routes/+layout.svelte`
- `frontend/src/routes/+layout.server.ts`
- `frontend/src/routes/transactions/+page.server.ts`
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/package.json`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java`

## Expected Output

- `frontend/src/routes/+page.server.ts`
- `frontend/src/routes/+page.svelte`
- `frontend/package.json`

## Verification

cd frontend && bun run check && grep -q 'layerchart' package.json
