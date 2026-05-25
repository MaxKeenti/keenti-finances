# S06: Debt Tracking

**Goal:** Create embroidery job debts per debtor (contact), record partial/full payments that auto-create INGRESS transactions, display debts with remaining balance in the UI and dashboard
**Demo:** Create embroidery job debts per debtor, record partial payments, see auto-created ingress transactions in transaction list and dashboard

## Must-Haves

- Flyway V5 migration creates `debt` and `debt_payment` tables with correct FK constraints
- Backend REST endpoints at /api/debts support full CRUD for debts and POST for payments
- Recording a debt payment auto-creates an INGRESS transaction via TransactionUseCase.create()
- SvelteKit /debts page lists all debts with debtor name, total, paid, remaining balance
- SvelteKit /debts/[id] page shows debt details and payment history, allows recording partial payments
- `./mvnw compile -q` exits 0; `cd frontend && bun run check` exits 0
- Debt domain model (Debt.java, DebtPayment.java) contains no Jakarta/Panache/Hibernate imports

## Proof Level

- This slice proves: integration

## Integration Closure

Upstream surfaces consumed: TransactionUseCase.create() from S03 for auto-ingress creation; Contact domain model and ContactRepository from S02 for debtor assignment; Category model from S02 for debt payment ingress categorization; auth middleware and SvelteKit proxy pattern from S01; sidebar/bottom-nav already wired with /debts link.\nNew wiring introduced: DebtService calls TransactionUseCase.create() to auto-generate INGRESS transactions on payment recording. DebtResource REST adapter at /api/debts. SvelteKit /debts and /debts/[id] routes proxied through existing [...path] catch-all.\nWhat remains: S07 (Public Subscription View) and S08 (Railway Deployment)

## Verification

- JBoss Logger structured logs on every debt CRUD and payment recording operation (entity type, operation, id, amount, remaining balance) in DebtService. REST resource returns structured JSON error bodies for 400 (validation) and 404 (not found). SvelteKit surfaces success/failure via sonner toast notifications. Auto-created ingress transactions are traceable by description referencing the debt.

## Tasks

- [x] **T01: Add Flyway V5 migration and full hexagonal debt backend with auto-ingress payment** `est:1h30m`
  ## Description
  - Files: `backend/src/main/resources/db/migration/V5__create_debt_tables.sql`, `backend/src/main/java/com/keenti/finances/domain/model/Debt.java`, `backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/DebtUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/DebtRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/DebtPaymentRepository.java`, `backend/src/main/java/com/keenti/finances/application/service/DebtService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtPaymentEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentResponse.java`
  - Verify: ./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Debt.java backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java; test $? -eq 1 && test -f backend/src/main/resources/db/migration/V5__create_debt_tables.sql && grep -q 'TransactionUseCase' backend/src/main/java/com/keenti/finances/application/service/DebtService.java

- [x] **T02: Build SvelteKit /debts CRUD page with debt cards, remaining balance, and create/edit dialogs** `est:1h`
  ## Description
  - Files: `frontend/src/routes/debts/+page.server.ts`, `frontend/src/routes/debts/+page.svelte`
  - Verify: cd frontend && bun run check && test -f src/routes/debts/+page.svelte && test -f src/routes/debts/+page.server.ts

- [x] **T03: Build SvelteKit /debts/[id] detail page with payment history and partial payment recording** `est:1h`
  ## Description
  - Files: `frontend/src/routes/debts/[id]/+page.server.ts`, `frontend/src/routes/debts/[id]/+page.svelte`
  - Verify: cd frontend && bun run check && test -f src/routes/debts/\[id\]/+page.svelte && test -f src/routes/debts/\[id\]/+page.server.ts

## Files Likely Touched

- backend/src/main/resources/db/migration/V5__create_debt_tables.sql
- backend/src/main/java/com/keenti/finances/domain/model/Debt.java
- backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java
- backend/src/main/java/com/keenti/finances/domain/port/in/DebtUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/DebtRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/DebtPaymentRepository.java
- backend/src/main/java/com/keenti/finances/application/service/DebtService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtPaymentEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentResponse.java
- frontend/src/routes/debts/+page.server.ts
- frontend/src/routes/debts/+page.svelte
- frontend/src/routes/debts/[id]/+page.server.ts
- frontend/src/routes/debts/[id]/+page.svelte
