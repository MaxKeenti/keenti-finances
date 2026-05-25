# S04: Financial Dashboard

**Goal:** Dashboard page at `/` showing monthly bar chart (income vs expenses), yearly trend line, and net balance card — all driven by real transaction data aggregated server-side via a new `/api/dashboard/summary` endpoint following the hexagonal pattern.
**Demo:** View monthly bar chart and yearly trend line with real transaction data; net balance reflects actual income minus expenses

## Must-Haves

- 1. `GET /api/dashboard/summary?year=2026` returns JSON with monthly ingress/egress totals, year totals, and all-time net balance
- 2. `./mvnw compile -q` exits 0 with no new warnings
- 3. Dashboard page at `/` renders a monthly bar chart, yearly trend line, and net balance card using Layerchart
- 4. `cd frontend && bun run check` exits 0 with no new errors
- 5. Empty-state (no transactions) renders gracefully with zero-value chart and $0.00 balance

## Proof Level

- This slice proves: integration — real backend aggregation consumed by real frontend chart rendering

## Integration Closure

Upstream surfaces consumed: TransactionRepository (findAll query pattern), Transaction domain model (amount, direction, transactionDate), existing SvelteKit proxy at `/api/[...path]`, shadcn-svelte Card component. New wiring: DashboardResource REST endpoint → SvelteKit load function → Layerchart components on `/` route. What remains: S05 (subscriptions), S06 (debts), S07 (public view), S08 (deployment).

## Verification

- DashboardService logs aggregation requests with year parameter and result counts. REST endpoint returns structured JSON errors for invalid year parameter.

## Tasks

- [x] **T01: Add backend dashboard aggregation endpoint with monthly/yearly summaries** `est:1h`
  ---
  estimated_steps: 8
  estimated_files: 8
  skills_used:
    - api-design
  ---
  - Files: `backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java`, `backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/DashboardUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`, `backend/src/main/java/com/keenti/finances/application/service/DashboardService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java`
  - Verify: ./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java; test $? -eq 1

- [x] **T02: Build SvelteKit dashboard page with Layerchart bar chart, trend line, and net balance card** `est:1h30m`
  ---
  estimated_steps: 9
  estimated_files: 6
  skills_used:
    - svelte-code-writer
    - svelte-core-bestpractices
  ---
  - Files: `frontend/package.json`, `frontend/src/routes/+page.svelte`, `frontend/src/routes/+page.server.ts`
  - Verify: cd frontend && bun run check && grep -q 'layerchart' package.json

## Files Likely Touched

- backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java
- backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java
- backend/src/main/java/com/keenti/finances/domain/port/in/DashboardUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java
- backend/src/main/java/com/keenti/finances/application/service/DashboardService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java
- frontend/package.json
- frontend/src/routes/+page.svelte
- frontend/src/routes/+page.server.ts
