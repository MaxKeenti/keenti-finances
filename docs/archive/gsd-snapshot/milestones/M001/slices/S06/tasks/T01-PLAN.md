---
estimated_steps: 44
estimated_files: 16
skills_used: []
---

# T01: Add Flyway V5 migration and full hexagonal debt backend with auto-ingress payment

## Description

Build the complete hexagonal backend for debt tracking: Flyway V5 migration creating `debt` and `debt_payment` tables, framework-free Debt and DebtPayment domain POJOs, DebtUseCase/DebtRepository/DebtPaymentRepository ports, DebtService application service that auto-creates INGRESS transactions via TransactionUseCase.create() when recording payments, PanacheDebtRepository and PanacheDebtPaymentRepository adapters, DebtEntity and DebtPaymentEntity Panache entities, and DebtResource REST adapter at /api/debts with full CRUD for debts plus POST /api/debts/{id}/payments for recording payments.

## Steps

1. Create Flyway V5 migration `V5__create_debt_tables.sql`:
   - `debt` table: id BIGSERIAL PK, contact_id BIGINT NOT NULL FK→contact(id), description VARCHAR(500) NOT NULL, total_amount DECIMAL(12,2) NOT NULL, status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','PAID')), created_at TIMESTAMP DEFAULT NOW()
   - `debt_payment` table: id BIGSERIAL PK, debt_id BIGINT NOT NULL FK→debt(id) ON DELETE CASCADE, amount DECIMAL(12,2) NOT NULL, payment_date DATE NOT NULL, transaction_id BIGINT FK→transaction(id), notes VARCHAR(500), created_at TIMESTAMP DEFAULT NOW()

2. Create domain POJOs:
   - `Debt.java`: id, contactId, description, totalAmount, status, createdAt — framework-free
   - `DebtPayment.java`: id, debtId, amount, paymentDate, transactionId, notes, createdAt — framework-free

3. Create ports:
   - `DebtUseCase.java` (inbound): list(), getById(), create(), update(), delete()
   - `DebtRepository.java` (outbound): findAll(), findById(), save(), update(), deleteById()
   - `DebtPaymentRepository.java` (outbound): findByDebtId(), save(), sumByDebtId()

4. Create `DebtService.java`:
   - Inject DebtRepository, DebtPaymentRepository, TransactionUseCase
   - create/update/delete/list/getById for debts with structured logging
   - recordPayment(debtId, amount, paymentDate, categoryId, notes): validates debt exists and is ACTIVE, validates payment won't exceed remaining balance, creates DebtPayment, calls TransactionUseCase.create() with INGRESS direction and description like "Debt payment: [debt description]", sets debt status to PAID if remaining is 0, returns the created payment
   - getRemainingBalance(debtId): totalAmount - sum of payments

5. Create Panache entities and adapters:
   - `DebtEntity.java` with PanacheEntity, FK to ContactEntity
   - `DebtPaymentEntity.java` with PanacheEntity, FK to DebtEntity and TransactionEntity
   - `PanacheDebtRepository.java` implementing DebtRepository
   - `PanacheDebtPaymentRepository.java` implementing DebtPaymentRepository with sum query

6. Create REST adapter:
   - `DebtResource.java` at /api/debts: GET list (enriched with contactName, totalPaid, remaining), GET /{id}, POST create, PUT /{id} update, DELETE /{id}
   - POST /api/debts/{id}/payments: record a payment, return payment with auto-created transaction id
   - GET /api/debts/{id}/payments: list payments for a debt
   - `DebtRequest.java`, `DebtResponse.java`, `DebtPaymentRequest.java`, `DebtPaymentResponse.java` records

## Must-Haves

- [ ] Debt.java and DebtPayment.java contain zero Jakarta/Panache/Hibernate imports
- [ ] Payment recording auto-creates INGRESS transaction via TransactionUseCase.create()
- [ ] Payment amount validation: cannot exceed remaining balance
- [ ] Debt status auto-transitions to PAID when remaining balance reaches 0
- [ ] Structured JBoss logging on all debt/payment operations
- [ ] V5 migration file exists and is syntactically valid SQL

## Verification

- `./mvnw compile -q` exits 0
- `grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Debt.java backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java` exits 1 (no matches)
- `test -f backend/src/main/resources/db/migration/V5__create_debt_tables.sql`
- `grep -q 'TransactionUseCase' backend/src/main/java/com/keenti/finances/application/service/DebtService.java`

## Observability Impact

- Signals added: JBoss structured logs for debt.create, debt.update, debt.delete, debt.payment.record with id, amount, remaining balance
- Inspection: /api/debts/{id} returns totalPaid and remaining fields; /api/debts/{id}/payments shows payment history with transaction_id linking to auto-created ingress
- Failure state: 400 for overpayment attempt, 404 for missing debt/contact, 400 for payment on PAID debt

## Inputs

- `backend/src/main/resources/db/migration/V4__create_subscription_tables.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`

## Expected Output

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

## Verification

./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Debt.java backend/src/main/java/com/keenti/finances/domain/model/DebtPayment.java; test $? -eq 1 && test -f backend/src/main/resources/db/migration/V5__create_debt_tables.sql && grep -q 'TransactionUseCase' backend/src/main/java/com/keenti/finances/application/service/DebtService.java
