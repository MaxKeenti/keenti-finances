---
estimated_steps: 30
estimated_files: 7
skills_used: []
---

# T01: Add backend dashboard aggregation endpoint with monthly/yearly summaries

---
estimated_steps: 8
estimated_files: 8
skills_used:
  - api-design
---

# T01: Add backend dashboard aggregation endpoint with monthly/yearly summaries

**Slice:** S04 — Financial Dashboard
**Milestone:** M001

## Description

Create a new hexagonal dashboard stack: DashboardSummary domain model (monthly breakdown + totals), DashboardUseCase inbound port, DashboardService application service that queries TransactionRepository and aggregates by month/direction, and DashboardResource REST adapter at `/api/dashboard/summary?year=YYYY`. The aggregation should use a new repository method with a JPQL query that groups by month and direction for the given year, plus an all-time net balance query. This keeps aggregation in SQL rather than loading all transactions into memory.

## Steps

1. Create `DashboardSummary` domain model in `domain/model/` — a POJO with: year (int), netBalance (BigDecimal, all-time), totalIngress (BigDecimal, year), totalEgress (BigDecimal, year), monthly (List of MonthSummary with month/ingress/egress fields)
2. Create `MonthSummary` domain model — simple POJO with month (int), ingress (BigDecimal), egress (BigDecimal)
3. Add `DashboardUseCase` inbound port in `domain/port/in/` with method `DashboardSummary getSummary(int year)`
4. Add `findMonthlySummary(int year)` and `getNetBalance()` methods to `TransactionRepository` outbound port
5. Implement `findMonthlySummary` in `PanacheTransactionRepository` using native SQL query: `SELECT EXTRACT(MONTH FROM transaction_date) as month, direction, SUM(amount) as total FROM transaction WHERE EXTRACT(YEAR FROM transaction_date) = :year GROUP BY month, direction ORDER BY month`. Implement `getNetBalance` with: `SELECT COALESCE(SUM(CASE WHEN direction='INGRESS' THEN amount ELSE -amount END), 0) FROM transaction`
6. Create `DashboardService` in `application/service/` implementing `DashboardUseCase` — calls repository methods, assembles DashboardSummary with all 12 months (filling zeros for months with no data), logs the request
7. Create `DashboardResource` at `@Path("/api/dashboard")` with `@GET @Path("/summary")` that takes `@QueryParam("year") @DefaultValue("current") String year`, parses year (defaulting to current year), calls DashboardUseCase, returns JSON response
8. Verify: `./mvnw compile -q` exits 0

## Must-Haves

- [ ] DashboardSummary and MonthSummary are framework-free POJOs (no Jakarta/Panache imports)
- [ ] SQL aggregation in repository, not in-memory in service layer
- [ ] All 12 months present in response (zero-filled for months without transactions)
- [ ] Net balance is all-time (not filtered by year)
- [ ] Structured JBoss logging in DashboardService

## Verification

- `./mvnw compile -q` exits 0
- `grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java` exits 1 (no framework imports in domain models)
- `test -f backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java`

## Inputs

- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`

## Expected Output

- `backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java`
- `backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/DashboardUseCase.java`
- `backend/src/main/java/com/keenti/finances/application/service/DashboardService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DashboardResource.java`

## Verification

./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/DashboardSummary.java backend/src/main/java/com/keenti/finances/domain/model/MonthSummary.java; test $? -eq 1
