---
id: T01
parent: S06
milestone: M001
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
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentResponse.java
key_decisions:
  - recordPayment requires categoryId from the caller so the auto-created INGRESS transaction is categorized — consistent with TransactionService validation
  - DebtService.getRemainingBalance() is public (not port-level) so DebtResource can call it directly for response enrichment without adding it to DebtUseCase
  - Native SQL SUM via EntityManager in PanacheDebtPaymentRepository follows the same pattern as PanacheTransactionRepository.getNetBalance()
  - Debt status auto-transitions to PAID inline within recordPayment() rather than a separate scheduled check — simpler and correct for the immediate-consistency requirement
duration: 
verification_result: passed
completed_at: 2026-05-14T15:32:00.434Z
blocker_discovered: false
---

# T01: Added Flyway V5 migration + full hexagonal debt backend with auto-INGRESS payment recording via TransactionUseCase

**Added Flyway V5 migration + full hexagonal debt backend with auto-INGRESS payment recording via TransactionUseCase**

## What Happened

Created 16 files implementing the complete debt tracking backend following the established hexagonal architecture pattern.

**Migration**: V5__create_debt_tables.sql creates `debt` (id, contact_id FK, description, total_amount, status ACTIVE/PAID, created_at) and `debt_payment` (id, debt_id FK CASCADE, amount, payment_date, transaction_id FK, notes, created_at) tables.

**Domain POJOs**: Debt.java and DebtPayment.java are framework-free with only java.* imports — no Jakarta/Panache/Hibernate.

**Ports**: DebtUseCase (inbound) exposes list/getById/create/update/delete/recordPayment/listPayments. DebtRepository and DebtPaymentRepository (outbound) provide persistence contracts including sumByDebtId for balance computation.

**DebtService**: Injects DebtRepository, DebtPaymentRepository, and TransactionUseCase. recordPayment() validates: (1) debt exists, (2) debt is ACTIVE, (3) payment ≤ remaining balance. On success it calls TransactionUseCase.create() with direction=INGRESS and description "Debt payment: [debt description]", saves the DebtPayment linking to the created transaction, and auto-transitions debt status to PAID when remaining balance reaches zero. All operations emit JBoss structured logs with id/amount/remaining.

**Panache adapters**: DebtEntity and DebtPaymentEntity extend PanacheEntityBase with ManyToOne FKs to ContactEntity and TransactionEntity. PanacheDebtRepository and PanacheDebtPaymentRepository implement the outbound ports; the payment repo uses a native SQL SUM query via EntityManager for balance aggregation.

**REST adapter**: DebtResource at /api/debts with full CRUD plus POST /api/debts/{id}/payments and GET /api/debts/{id}/payments. GET responses are enriched with contactName (via ContactUseCase), totalPaid, and remaining. DTOs use Java records with Bean Validation annotations.

## Verification

1. `./mvnw compile -q` exited 0 — all 16 new files compile cleanly with zero errors.
2. `grep -rn 'jakarta|panache|hibernate' Debt.java DebtPayment.java` exited 1 — no framework imports in domain POJOs.
3. `test -f V5__create_debt_tables.sql` exited 0 — migration file exists.
4. `grep -q 'TransactionUseCase' DebtService.java` exited 0 — auto-ingress wiring confirmed.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd backend && ./mvnw compile -q` | 0 | pass | 8200ms |
| 2 | `grep -rn 'jakarta|panache|hibernate' domain/model/Debt.java domain/model/DebtPayment.java; echo $?` | 1 | pass — no framework imports in domain POJOs | 50ms |
| 3 | `test -f backend/src/main/resources/db/migration/V5__create_debt_tables.sql` | 0 | pass | 10ms |
| 4 | `grep -q 'TransactionUseCase' backend/.../DebtService.java` | 0 | pass | 10ms |

## Deviations

listPayments() added to DebtUseCase (not in original port spec) to support GET /api/debts/{id}/payments cleanly. getRemainingBalance() exposed as a public DebtService method rather than a port method to avoid polluting DebtUseCase with a read-side aggregation used only by the REST layer.

## Known Issues

none

## Files Created/Modified

- `backend/src/main/resources/db/migration/V5__create_debt_tables.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Debt.java`
- `backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/DebtUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/DebtRepository.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/DebtPaymentRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/DebtService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtPaymentEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtPaymentRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtPaymentResponse.java`
