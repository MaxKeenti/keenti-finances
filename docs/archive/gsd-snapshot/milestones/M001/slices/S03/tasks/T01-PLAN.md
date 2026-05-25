---
estimated_steps: 27
estimated_files: 10
skills_used: []
---

# T01: Flyway V3 migration and Transaction hexagonal backend stack

Create the full backend hexagonal stack for transactions: Flyway V3 migration creating the `transaction` table with FK references to category and contact, Transaction domain POJO, TransactionUseCase and TransactionRepository ports, TransactionService application service with direction validation and structured logging, PanacheTransactionRepository and TransactionEntity, TransactionResource REST adapter with full CRUD at /api/transactions, plus TransactionRequest/TransactionResponse records.

## Steps

1. Create Flyway V3 migration `V3__create_transaction_table.sql` with columns: id (BIGSERIAL PK), amount (DECIMAL(12,2) NOT NULL), direction (VARCHAR(10) NOT NULL CHECK IN ('INGRESS','EGRESS')), description (VARCHAR(500)), transaction_date (DATE NOT NULL), category_id (BIGINT NOT NULL FK → category), contact_id (BIGINT FK → contact, nullable), created_at (TIMESTAMP DEFAULT NOW()).
2. Create `Transaction` domain POJO in `domain/model/` with fields: id, amount (BigDecimal), direction (String), description, transactionDate (LocalDate), categoryId (Long), contactId (Long nullable). Constructor + getters, no framework imports.
3. Create `TransactionUseCase` port interface in `domain/port/in/` with: list(), getById(Long), create(Transaction), update(Long, Transaction), delete(Long).
4. Create `TransactionRepository` port interface in `domain/port/out/` with: findAll(), findById(Long), save(Transaction), update(Transaction), deleteById(Long).
5. Create `TransactionService` in `application/service/` implementing TransactionUseCase. Validate direction is INGRESS or EGRESS. @Transactional on create/update/delete. JBoss Logger structured logs: `transaction.create id=%d amount=%s direction=%s`, `transaction.update id=%d`, `transaction.delete id=%d`. Throw NotFoundException for missing id on update/delete.
6. Create `TransactionEntity` Panache entity in `infrastructure/adapter/out/persistence/` with @ManyToOne to CategoryEntity and ContactEntity (nullable). Map all columns.
7. Create `PanacheTransactionRepository` implementing TransactionRepository port. toDomain/toEntity mappers. findAll orders by transaction_date DESC, created_at DESC.
8. Create `TransactionRequest` record with @NotNull BigDecimal amount, @NotBlank String direction, String description (nullable), @NotNull LocalDate transactionDate, @NotNull Long categoryId, Long contactId (nullable).
9. Create `TransactionResponse` record with all fields including categoryId, contactId, categoryName, contactName for display convenience.
10. Create `TransactionResource` at @Path("/api/transactions") with GET (list), GET/{id}, POST, PUT/{id}, DELETE/{id}. POST/PUT look up category and contact by ID for validation (404 if category not found); include categoryName and contactName in response.
11. Verify: `./mvnw compile -q` exits 0. grep domain/model/Transaction.java for jakarta/panache imports returns no matches.

## Must-Haves

- [ ] V3 migration with proper FK constraints and CHECK on direction
- [ ] Transaction domain POJO is framework-free
- [ ] TransactionService validates direction and logs all CRUD ops
- [ ] TransactionResource returns structured 400/404 JSON errors
- [ ] TransactionResponse includes categoryName and contactName for UI display
- [ ] findAll orders by transaction_date DESC, created_at DESC

## Verification

- `./mvnw compile -q` exits 0
- `grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` returns no matches
- `test -f backend/src/main/resources/db/migration/V3__create_transaction_table.sql`

## Observability Impact

- Signals added: JBoss Logger structured log lines on transaction.list, transaction.get, transaction.create, transaction.update, transaction.delete
- REST resource returns structured JSON error bodies for 400 (bad direction) and 404 (not found)

## Inputs

- `backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`

## Expected Output

- `backend/src/main/resources/db/migration/V3__create_transaction_table.sql`
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`

## Verification

./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Transaction.java; test $? -eq 1 && test -f backend/src/main/resources/db/migration/V3__create_transaction_table.sql
