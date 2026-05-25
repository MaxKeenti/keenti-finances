# S04: Financial Dashboard — UAT

**Milestone:** M001
**Written:** 2026-05-14T02:00:04.049Z

# S04: Financial Dashboard — UAT

**Milestone:** M001
**Written:** 2026-05-13

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: All verification is structural — build artifacts, type checks, file existence, and code inspection confirm that the backend endpoint exists and returns correct shape, and the frontend page compiles and references the endpoint. Live-runtime validation (browser render of real chart bars) requires a running Quarkus + SvelteKit stack and is deferred to S08 Railway deployment smoke test.

## Preconditions

1. Backend is compiled: `cd backend && ./mvnw compile -q` exits 0
2. Frontend dependencies installed: `cd frontend && bun install`
3. Frontend type-checks: `cd frontend && bun run check` exits 0 with 0 errors

## Smoke Test

`grep -q 'dashboard/summary' frontend/src/routes/+page.server.ts` → exit 0 confirms the load function references the aggregation endpoint.

## Test Cases

### 1. Backend endpoint structure

1. Read `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java`
2. Confirm `@Path("/api/dashboard/summary")` and `@QueryParam("year")` are present
3. Confirm year validation (1900–9999) with structured JSON error response
4. **Expected:** Endpoint is wired to `DashboardUseCase`, validates year, returns `DashboardSummary` serialized as JSON

### 2. Domain model hexagonal purity

1. Run: `grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java`
2. **Expected:** grep exits 1 (no matches) — domain models are pure Java with no framework imports

### 3. 12-month zero-fill

1. Read `PanacheTransactionRepository.java` aggregation method
2. Confirm months 1–12 are guaranteed in the result even when no transactions exist for some months
3. **Expected:** Repository initializes a map with zero values for all 12 months before overlaying query results

### 4. Frontend dashboard page renders all three sections

1. Read `frontend/src/routes/+page.svelte`
2. Confirm presence of: monthly bar chart SVG section, trend line SVG section, net balance card, year selector
3. **Expected:** All three UI sections present; d3-scale used for scale math; no layerchart component imports causing type errors

### 5. Frontend load function proxies correctly

1. Read `frontend/src/routes/+page.server.ts`
2. Confirm it fetches from `/api/dashboard/summary?year=...` and returns structured data to the page
3. **Expected:** Load function sends year parameter, handles non-OK responses, shapes `monthlyData`, `yearTotal`, and `netBalance` for the page component

### 6. Empty-state (no transactions)

1. With an empty database or a year with no transactions, the endpoint returns all 12 months with 0 income and 0 expense
2. **Expected:** Dashboard renders with a zero-value bar chart and "$0.00" net balance — no errors, no blank page

## Edge Cases

### Invalid year parameter

1. Call `GET /api/dashboard/summary?year=abc` or `?year=1800`
2. **Expected:** DashboardResource returns HTTP 400 with structured JSON body `{ "error": "...", "parameter": "year" }` (not a raw string or stack trace)

### Year with sparse transactions

1. Seed only January and December transactions for a given year
2. Call `GET /api/dashboard/summary?year=<that year>`
3. **Expected:** Response contains 12 MonthSummary entries; months 2–11 have `income: 0, expenses: 0`

## Failure Signals

- `./mvnw compile -q` exits non-zero → backend compilation broken, check DashboardService/DashboardResource wiring
- `bun run check` reports errors (not just warnings) → Svelte type mismatch in +page.svelte or +page.server.ts
- `grep -q 'layerchart' frontend/package.json` exits 1 → dependency removed, d3-scale charts may still work but plan parity is lost
- `grep -rn 'jakarta' DashboardSummary.java MonthSummary.java` exits 0 → framework leaked into domain; hexagonal purity broken

## Not Proven By This UAT

- Live browser rendering of the SVG bar chart and trend line with real pixel output
- Network call from SvelteKit SSR to Quarkus in a running stack
- Chart responsiveness on mobile Safari
- Dashboard reload after adding a new transaction in the same session
- Railway production deployment of the dashboard endpoint

## Notes for Tester

- The 4 pre-existing svelte-check warnings (state_referenced_locally in categories, contacts, login, transactions pages) are known and not introduced by S04. Ignore them.
- layerchart is installed as a package but its component API is NOT used in +page.svelte — this is intentional. Using layerchart components in Svelte 5 causes type errors. d3-scale is used instead for scale calculations in inline SVG.
- Live end-to-end rendering verification is deferred to S08 (Railway deployment smoke test).
