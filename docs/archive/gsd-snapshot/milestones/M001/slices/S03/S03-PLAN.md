# S03: Transaction Tracking

**Goal:** Full CRUD for transactions (ingresses and egresses) with category assignment, optional contact, amount in MXN, date, and description. Transactions persist to PostgreSQL via hexagonal backend, accessible through SvelteKit UI with form validation and toast notifications.
**Demo:** Add ingresses and egresses with categories, see them listed and persisted; edit and delete work

## Must-Haves

- 1. `./mvnw compile -q` exits 0 — backend compiles clean with Transaction domain model, ports, service, Panache adapter, REST resource, and Flyway V3 migration.
- 2. `bun run check` exits 0 with 0 errors — SvelteKit /transactions route type-checks clean.
- 3. Transaction CRUD operations work end-to-end: create with amount/direction/category/date/description, list all, edit, delete.
- 4. Category dropdown populated from /api/categories; optional contact dropdown from /api/contacts.
- 5. Domain layer remains framework-free (no Jakarta/Panache imports in domain package).
- 6. Structured JSON error bodies returned for 400 (validation), 404 (not found) from REST resource.
- 7. Navigation to /transactions already wired in sidebar and bottom-nav (from S01 app shell).

## Proof Level

- This slice proves: integration — real SvelteKit → proxy → Quarkus → PostgreSQL round-trip required to verify

## Integration Closure

Upstream surfaces consumed: Category domain model and CategoryRepository (S02), Contact domain model and ContactRepository (S02), SvelteKit proxy pattern (S01), auth guard (S01), app shell sidebar/bottom-nav with /transactions nav item (S01).
New wiring: Transaction REST endpoint at /api/transactions, SvelteKit /transactions route with server actions.
What remains: S04 (dashboard charts using transaction aggregation), S06 (debt payments auto-creating ingress transactions).

## Verification

- JBoss Logger structured logs on every transaction CRUD operation (entity type, operation, id, amount, direction) in TransactionService. REST resource returns structured JSON error bodies (400/404). SvelteKit surfaces success/failure via sonner toasts.

## Tasks

- [x] **T01: Flyway V3 migration and Transaction hexagonal backend stack** `est:45m`
  Create the full backend hexagonal stack for transactions: Flyway V3 migration creating the `transaction` table with FK references to category and contact, Transaction domain POJO, TransactionUseCase and TransactionRepository ports, TransactionService application service with direction validation and structured logging, PanacheTransactionRepository and TransactionEntity, TransactionResource REST adapter with full CRUD at /api/transactions, plus TransactionRequest/TransactionResponse records.
  - Files: `backend/src/main/resources/db/migration/V3__create_transaction_table.sql`, `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java`, `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java`
  - Verify: ./mvnw compile -q && grep -rn 'jakarta\|panache\|hibernate' backend/src/main/java/com/keenti/finances/domain/model/Transaction.java; test $? -eq 1 && test -f backend/src/main/resources/db/migration/V3__create_transaction_table.sql

- [x] **T02: SvelteKit /transactions CRUD page with category and contact selectors** `est:45m`
  Build the SvelteKit /transactions route following the established CRUD page pattern from S02: single Zod schema for create/update, superforms for validation, plain enhance for delete, sonner toast notifications, shadcn-svelte Dialog for edit/delete, Table for listing. Transaction form includes: amount (number input), direction (select: INGRESS/EGRESS), description (text input), transaction date (date input), category (select populated from /api/categories), and optional contact (select populated from /api/contacts).
  - Files: `frontend/src/routes/transactions/+page.server.ts`, `frontend/src/routes/transactions/+page.svelte`
  - Verify: cd /Users/moonstone/Source/Personal/keenti-finances/.gsd/worktrees/M001/frontend && bun run check && test -f src/routes/transactions/+page.svelte && test -f src/routes/transactions/+page.server.ts

## Files Likely Touched

- backend/src/main/resources/db/migration/V3__create_transaction_table.sql
- backend/src/main/java/com/keenti/finances/domain/model/Transaction.java
- backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/TransactionRepository.java
- backend/src/main/java/com/keenti/finances/application/service/TransactionService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResource.java
- frontend/src/routes/transactions/+page.server.ts
- frontend/src/routes/transactions/+page.svelte
