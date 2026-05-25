---
id: S06
parent: M001
milestone: M001
provides:
  - Debt domain model (Debt, DebtPayment) and full hexagonal port/service/adapter stack
  - DebtUseCase port with CRUD + payment recording + listPayments
  - Auto-INGRESS transaction creation via TransactionUseCase.create() on debt payment
  - DebtResource REST adapter at /api/debts with structured error responses
  - Flyway V5 migration for debt and debt_payment tables
  - SvelteKit /debts listing page with CRUD dialogs
  - SvelteKit /debts/[id] detail page with payment history and partial payment recording
requires:
  - slice: S01
    provides: auth middleware and SvelteKit proxy pattern
  - slice: S02
    provides: Contact domain model and ContactRepository for debtor assignment; Category model for debt payment ingress categorization
  - slice: S03
    provides: TransactionUseCase.create() for auto-ingress creation; Ingress transaction model and persistence
affects:
  - S08
key_files:
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
  - frontend/src/routes/debts/+page.server.ts
  - frontend/src/routes/debts/+page.svelte
  - frontend/src/routes/debts/[id]/+page.server.ts
  - frontend/src/routes/debts/[id]/+page.svelte
key_decisions:
  - recordPayment() requires categoryId from the caller so the auto-created INGRESS transaction is categorized — consistent with TransactionService validation
  - getRemainingBalance() is a public DebtService method (not port-level) so DebtResource can call it directly without polluting DebtUseCase with a read-side aggregation
  - Native SQL SUM via EntityManager in PanacheDebtPaymentRepository follows the same pattern as PanacheTransactionRepository.getNetBalance()
  - Debt status auto-transitions to PAID inline within recordPayment() for immediate consistency rather than a scheduled check
  - Zod 4: use .positive('message') instead of required_error on z.number() — consistent with all other schemas in project
  - contactId validated as z.coerce.number().positive() so the HTML select's default value of 0 fails validation
  - Category selector on payment form filters to INGRESS-only client-side to ensure auto-created transactions are categorized correctly
  - listPayments() added to DebtUseCase (beyond original spec) to cleanly support GET /api/debts/{id}/payments endpoint
patterns_established:
  - DebtService calls TransactionUseCase.create() with direction=INGRESS to auto-generate income transactions on debt payment — pattern for any future auto-transaction use cases
  - Native SQL SUM via EntityManager for aggregation in Panache repositories (getRemainingBalance, getNetBalance) — avoids loading all records into memory
  - Debt status auto-transition inline within the write operation (recordPayment) rather than a separate scheduled reconciliation
observability_surfaces:
  - JBoss Logger structured logs on every debt CRUD and payment recording operation (entity type, operation, id, amount, remaining balance) in DebtService
  - REST resource returns structured JSON error bodies for 400 (validation) and 404 (not found)
  - Auto-created ingress transactions are traceable by description referencing the debt ID
  - SvelteKit surfaces success/failure via sonner toast notifications
drill_down_paths:
  - milestones/M001/slices/S06/tasks/T01-SUMMARY.md
  - milestones/M001/slices/S06/tasks/T02-SUMMARY.md
  - milestones/M001/slices/S06/tasks/T03-SUMMARY.md
duration: ""
verification_result: passed
completed_at: 2026-05-14T15:44:37.022Z
blocker_discovered: false
---

# S06: Debt Tracking

**Full hexagonal debt backend with auto-INGRESS payment recording wired to TransactionUseCase, plus SvelteKit /debts and /debts/[id] pages with superforms-backed CRUD and partial payment recording**

## What Happened

S06 delivered end-to-end debt tracking across three tasks. T01 established the full hexagonal debt stack: Flyway V5 migration creating `debt` and `debt_payment` tables, pure domain POJOs (Debt.java, DebtPayment.java) with zero framework imports, DebtUseCase and DebtRepository ports, DebtService application service that calls TransactionUseCase.create() to auto-generate INGRESS transactions on every payment recording, Panache persistence adapters using native SQL SUM via EntityManager for remaining balance aggregation (following the pattern from PanacheTransactionRepository), and DebtResource REST adapter with structured JSON error bodies for 400 and 404 responses. Key design decisions: categoryId is provided by the caller at payment time (consistent with TransactionService validation); getRemainingBalance() is a public DebtService method rather than a port method to avoid polluting DebtUseCase with a read-side aggregation; debt status auto-transitions to PAID inline within recordPayment() for immediate consistency; listPayments() was added to DebtUseCase (beyond original spec) to cleanly support GET /api/debts/{id}/payments. T02 built the /debts listing page with debt cards showing debtor name, MXN balance breakdown (total/paid/remaining), status badge, and create/edit/delete dialogs backed by superforms + Zod 4. Zod 4 required using .positive('message') instead of required_error on z.number(); contactId uses z.coerce.number().positive() so the default HTML select value of 0 fails validation. T03 built the /debts/[id] detail page with payment history table, progress bar, and a superforms-backed partial payment form pre-filled with the remaining balance. Category selector filters to INGRESS-only categories client-side to ensure auto-created transactions are categorized correctly. Native textarea replaced the missing shadcn Textarea component with no functional difference. All verification evidence: ./mvnw compile -q exits 0; bun run check exits 0 with 0 errors and 7 pre-existing warnings; domain models contain no jakarta/panache/hibernate imports; V5 migration file present; DebtService references TransactionUseCase; all 4 frontend route files confirmed present.

## Verification

1. `./mvnw compile -q` exited 0 — all 16 backend files compile cleanly. 2. `bun run check` exited 0 with 0 errors and 7 pre-existing warnings (none in debts routes). 3. `grep -rni 'jakarta|panache|hibernate' Debt.java DebtPayment.java` exited 1 — no framework imports in domain POJOs. 4. `test -f V5__create_debt_tables.sql` exited 0 — migration file present. 5. `grep -q 'TransactionUseCase' DebtService.java` exited 0 — auto-ingress wiring confirmed. 6. All 4 frontend files (`/debts/+page.svelte`, `/debts/+page.server.ts`, `/debts/[id]/+page.svelte`, `/debts/[id]/+page.server.ts`) confirmed present.

## Requirements Advanced

None.

## Requirements Validated

None.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

listPayments() added to DebtUseCase (not in original port spec) to support GET /api/debts/{id}/payments cleanly. getRemainingBalance() exposed as a public DebtService method rather than a port method. Native textarea used instead of missing shadcn Textarea component in /debts/[id] — no functional difference.

## Known Limitations

none

## Follow-ups

None.

## Files Created/Modified

None.
