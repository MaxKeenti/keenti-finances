# S02: Soft deletes and trash view

**Goal:** Delete any entity (transaction, category, contact, subscription, debt) and have it disappear from all standard views and dashboards; open a unified Trash page to see all soft-deleted items; restore or permanently delete from Trash.
**Demo:** Delete a transaction — it disappears from the list and dashboard; open Trash page, see it listed; click restore, it reappears in the normal view

## Must-Haves

- `deleted_at TIMESTAMP` column (nullable) on all 5 data tables via Flyway V11
- Hibernate `softDelete` @Filter stacked alongside existing `userScope` — both enabled per-session
- All existing `delete()` service methods changed to set `deleted_at` instead of SQL DELETE
- New `restore()` and `listDeleted()` methods on all relevant services
- REST endpoints: `GET /api/trash`, `POST /api/trash/{type}/{id}/restore`, `DELETE /api/trash/{type}/{id}`
- Frontend delete actions call soft-delete (existing UX unchanged except toast says "moved to trash")
- Frontend `/trash` route showing all deleted items grouped by type with restore and permanent-delete actions
- Dashboard queries auto-exclude soft-deleted rows (Hibernate filter handles this)
- Soft-deleted items are user-scoped (both filters active — no cross-user trash visibility)

## Threat Surface

- **Abuse**: Permanent-delete endpoint must verify ownership (userScope filter); restore of another user's item must return 404
- **Data exposure**: Trash listing must be user-scoped — soft-deleted items are still private data
- **Input trust**: Entity type path parameter must be validated against known types; reject unknown types with 400

## Requirement Impact

- **Requirements touched**: R019 (soft deletes), R020 (trash view), R028 (CRUD completeness)
- **Re-verify**: All delete flows across transactions, categories, contacts, subscriptions, debts
- **Decisions revisited**: None — soft-delete architecture was decided in M003 context (Hibernate @Filter stacking)

## Proof Level

- This slice proves: integration — soft-delete, restore, and trash view work end-to-end through the full stack
- Real runtime required: yes (dev server for frontend verification)
- Human/UAT required: yes (visual verification of Trash page and restore flow)

## Verification

- `./mvnw test` — all existing tests pass (delete behavior changed from hard to soft)
- `cd frontend && bun run check` — no type errors in new Trash route and modified delete actions
- Manual: delete a transaction, verify it disappears from list; open /trash, verify it appears; restore it, verify it returns to list

## Observability / Diagnostics

- Runtime signals: Service layer logs `entity.soft_deleted` and `entity.restored` with entity type, id, and userId
- Inspection surfaces: `GET /api/trash` returns all soft-deleted items for the current user; DB query `SELECT * FROM transaction WHERE deleted_at IS NOT NULL` for direct inspection
- Failure visibility: Restore of non-existent or non-deleted entity returns 404; permanent delete of active (non-deleted) entity returns 404
- Redaction constraints: None — no secrets in trash data

## Integration Closure

- Upstream surfaces consumed: S01's `userScope` Hibernate filter, `UserContext` CDI bean, `UserScopeFilter` ContainerRequestFilter
- New wiring introduced in this slice: `softDelete` Hibernate filter stacked alongside `userScope`; `/trash` frontend route; trash REST endpoints
- What remains before the milestone is truly usable end-to-end: S03 (color picker), S04 (theme settings), S05 (onboarding)

## Tasks

- [ ] **T01: Flyway V11 migration — add deleted_at column to all data tables** `est:30m`
  - Why: Schema foundation for soft deletes. All 5 data tables need a nullable `deleted_at TIMESTAMP` column. Must come before any Hibernate filter or service changes.
  - Files: `backend/src/main/resources/db/migration/V11__soft_delete_columns.sql`
  - Do: Create V11 migration adding `deleted_at TIMESTAMP` (nullable, default NULL) to category, contact, transaction, subscription, debt tables. Add index on `deleted_at` for each table (partial index WHERE deleted_at IS NOT NULL for efficient trash queries).
  - Verify: `./mvnw quarkus:dev -Dquarkus.http.port=0 -Dquarkus.devservices.enabled=true`
  - Done when: Migration runs without error; all 5 tables have deleted_at column

- [ ] **T02: Hibernate softDelete filter and entity annotations** `est:1h`
  - Why: Defense-in-depth — standard queries must never return soft-deleted rows. Same @FilterDef/@Filter pattern established by S01 for userScope.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java`
  - Do: Add `@FilterDef(name = "softDelete", defaultCondition = "deleted_at IS NULL")` and `@Filter(name = "softDelete")` to all 5 entities. Add `deletedAt` field (LocalDateTime, nullable) to all entities. Enable the softDelete filter in UserScopeFilter alongside userScope. Note: softDelete filter has no parameters — it uses a fixed defaultCondition.
  - Verify: `./mvnw test`
  - Done when: Compilation succeeds; softDelete filter enabled per-session; existing tests pass (soft-deleted rows excluded from standard queries)

- [ ] **T03: Service layer — soft-delete, restore, and list-deleted methods** `est:1h30m`
  - Why: The domain service layer needs to change delete semantics (set deleted_at instead of SQL DELETE) and add restore/list-deleted capabilities. Repository ports need new methods.
  - Files: `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`, `backend/src/main/java/com/keenti/finances/application/service/ContactService.java`, `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`, `backend/src/main/java/com/keenti/finances/application/service/DebtService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheTransactionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/DebtUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/model/Category.java`, `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`, `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`, `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`, `backend/src/main/java/com/keenti/finances/domain/model/Debt.java`
  - Do: (1) Add `deletedAt` field to all 5 domain models. (2) Change `delete(id)` in each service to set `deletedAt = LocalDateTime.now()` and persist (soft-delete). (3) Add `restore(id)` to each service — disable softDelete filter temporarily, find by id, set deletedAt to null, persist. (4) Add `listDeleted()` to each service — disable softDelete filter, query WHERE deleted_at IS NOT NULL AND user_id = currentUser. (5) Add `permanentDelete(id)` — disable softDelete filter, find by id, SQL DELETE. (6) Add corresponding methods to use-case ports and repository ports.
  - Verify: `./mvnw test`
  - Done when: All 5 services have soft-delete, restore, list-deleted, and permanent-delete; existing tests pass with soft-delete semantics

- [ ] **T04: REST trash endpoints** `est:1h`
  - Why: Frontend needs API surface for trash operations. A unified TrashResource is cleaner than adding restore/trash methods to each entity resource.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TrashResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TrashResponse.java`
  - Do: Create `TrashResource` with: (1) `GET /api/trash` — aggregates listDeleted() from all 5 services into a unified response with entity type tags. (2) `POST /api/trash/{type}/{id}/restore` — dispatches to the correct service's restore(). (3) `DELETE /api/trash/{type}/{id}` — dispatches to the correct service's permanentDelete(). Type parameter validated against enum {transaction, category, contact, subscription, debt}. TrashResponse wraps entity data with type discriminator and deletedAt timestamp.
  - Verify: `./mvnw test`
  - Done when: Three trash endpoints compile and respond correctly; invalid type returns 400; non-existent id returns 404

- [ ] **T05: Frontend — wire existing delete actions to soft-delete** `est:45m`
  - Why: Existing delete buttons should now perform soft-delete. Backend behavior changed (T03), but frontend toast messages and redirects may need updating.
  - Files: `frontend/src/routes/transactions/+page.server.ts`, `frontend/src/routes/transactions/[id]/+page.server.ts`, `frontend/src/routes/categories/+page.svelte`, `frontend/src/routes/contacts/+page.svelte`, `frontend/src/routes/subscriptions/+page.svelte`, `frontend/src/routes/debts/+page.svelte`, `frontend/src/routes/transactions/+page.svelte`, `frontend/src/routes/transactions/[id]/+page.svelte`
  - Do: Update toast messages from "deleted" to "Moved to trash" across all entity pages. Backend already returns 204 on soft-delete so no server-side changes needed on the frontend proxy layer. Optionally add an "Undo" link in the toast that calls restore (nice-to-have, can defer).
  - Verify: `cd frontend && bun run check`
  - Done when: All delete actions show "Moved to trash" toast; svelte-check passes

- [ ] **T06: Frontend Trash page with restore and permanent-delete** `est:2h`
  - Why: Users need to see and recover soft-deleted items. This is the primary user-visible deliverable of S02.
  - Files: `frontend/src/routes/trash/+page.svelte`, `frontend/src/routes/trash/+page.server.ts`, `frontend/src/lib/components/sidebar.svelte`
  - Do: (1) Create `/trash` route with +page.server.ts that fetches GET /api/trash. (2) Build Trash page showing deleted items in a data table with columns: Name/description, Type (badge), Deleted date. (3) Add restore action (POST /api/trash/{type}/{id}/restore) and permanent-delete action (DELETE /api/trash/{type}/{id}) per item. (4) Add "Trash" link to sidebar navigation. (5) Use existing shadcn-svelte table, badge, and button components. (6) Empty state when no deleted items.
  - Verify: `cd frontend && bun run check`
  - Done when: /trash route renders deleted items; restore returns item to normal view; permanent delete removes from trash; sidebar has Trash link; svelte-check passes

## Files Likely Touched

- `backend/src/main/resources/db/migration/V11__soft_delete_columns.sql`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/TransactionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/DebtPaymentEntity.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserScopeFilter.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TrashResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TrashResponse.java`
- `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`
- `backend/src/main/java/com/keenti/finances/application/service/ContactService.java`
- `backend/src/main/java/com/keenti/finances/application/service/TransactionService.java`
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`
- `backend/src/main/java/com/keenti/finances/application/service/DebtService.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/TransactionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/port/in/DebtUseCase.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`
- `backend/src/main/java/com/keenti/finances/domain/model/Debt.java`
- `frontend/src/routes/trash/+page.svelte`
- `frontend/src/routes/trash/+page.server.ts`
- `frontend/src/lib/components/sidebar.svelte`
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/contacts/+page.svelte`
- `frontend/src/routes/subscriptions/+page.svelte`
- `frontend/src/routes/debts/+page.svelte`
