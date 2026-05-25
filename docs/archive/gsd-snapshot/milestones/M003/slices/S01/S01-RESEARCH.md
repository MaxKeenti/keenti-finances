# S01 Research: Multi-user data foundation

**Depth:** Deep research — this slice retrofits user-scoping onto every entity and query path, introduces Hibernate @Filter (not yet used anywhere), adds JIT provisioning, and rewrites the proxy. One missed query is a data leak.

## Summary

S01 adds `user_id` FK to all 6 data tables (category, contact, transaction, subscription, debt, debt_payment), introduces Hibernate `@FilterDef`/`@Filter` for automatic query scoping, creates a `UserContext` @RequestScoped bean, injects `X-WorkOS-User-Id` into the SvelteKit proxy, and migrates all existing data to user 1. Child tables (subscription_member, payment_record, debt_payment) inherit scoping through their parent's user_id — no direct user_id column needed.

## Recommendation

Use Hibernate `@FilterDef` with `@Filter` on each entity. Enable filters in a shared base method or CDI interceptor that runs before every repository call. Native SQL queries (3 total) must add `WHERE user_id = :userId` manually — filters don't apply to native queries. The proxy header injection is straightforward; the session already has `user.id` in `event.locals.session.user.id`.

## Implementation Landscape

### Entities needing user_id column (direct ownership)
| Entity | Table | Notes |
|--------|-------|-------|
| CategoryEntity | category | Direct user_id FK. Drop UNIQUE on name, make it UNIQUE(user_id, name) |
| ContactEntity | contact | Direct user_id FK |
| TransactionEntity | transaction | Direct user_id FK |
| SubscriptionEntity | subscription | Direct user_id FK |
| DebtEntity | debt | Direct user_id FK |

### Child entities (scoped through parent, NO user_id needed)
| Entity | Table | Scoped via |
|--------|-------|-----------|
| SubscriptionMemberEntity | subscription_member | subscription.user_id |
| PaymentRecordEntity | payment_record | subscription.user_id (via subscription_member or subscription) |
| DebtPaymentEntity | debt_payment | debt.user_id |

### UserEntity changes
Current: `id`, `username`, `password_hash` (nullable since V9)
Needs: add `workos_id` column (VARCHAR, UNIQUE, nullable initially), keep username/password_hash for backward compat during migration

### Repositories with native SQL (must add manual WHERE user_id)
1. `PanacheTransactionRepository.findMonthlySummary()` — native SQL with `GROUP BY` (line 70)
2. `PanacheTransactionRepository.getNetBalance()` — native SQL SUM (line 102)
3. `PanacheDebtPaymentRepository.sumByDebtId()` — native SQL SUM (line 36)

### Repositories using Panache static queries (auto-scoped by @Filter)
- `PanacheCategoryRepository` — `listAll()`, `findByIdOptional()`, `deleteById()`
- `PanacheContactRepository` — `listAll()`, `findByIdOptional()`, `deleteById()`
- `PanacheTransactionRepository` — `find()`, `findByIdOptional()`, `deleteById()`, `find("subscription.id")`
- `PanacheSubscriptionRepository` — `find()`, `findByIdOptional()`, `deleteById()`, `find("tokenUuid")`, `find("nextBillingDate")`
- `PanacheDebtRepository` — `find()`, `findByIdOptional()`, `deleteById()`, `find("contact.id")`
- `PanacheDebtPaymentRepository` — `find("debt.id")`
- `PanacheSubscriptionMemberRepository` — `find("subscription.id")`, `deleteById()`
- `PanachePaymentRecordRepository` — `find("subscription.id")`

### PublicSubscriptionResource — EXCEPTION
`PublicSubscriptionResource` at `/api/public/subscriptions/{token}` uses token-based access, NOT user-scoped. This endpoint must bypass user-scope filters. It queries by `tokenUuid` which is unique. The Hibernate filter must NOT be enabled for this path. Options:
- Don't enable filters in the public resource (filters are opt-in per session)
- Use a separate unfiltered EntityManager/session

### SvelteKit proxy changes
File: `frontend/src/routes/api/[...path]/+server.ts`
Current: forwards all headers except host/connection/transfer-encoding
Needed: add `X-WorkOS-User-Id` header from `event.locals.session.user.id`
The session is already available via hooks.server.ts which sets `event.locals.session`.
Public paths (`/public/*`) won't have a session — proxy should NOT set the header for those.

### Quarkus UserContext bean
New file: `infrastructure/adapter/in/rest/UserContext.java` (or similar)
- `@RequestScoped` CDI bean
- Reads `X-WorkOS-User-Id` from `@HeaderParam` or JAX-RS `@Context HttpHeaders`
- Resolves to local `app_user.id` via `workos_id` column lookup
- JIT provisioning: if no local user found, create one with the WorkOS ID
- Caches resolved user ID for the request lifetime
- Injected into every repository to enable Hibernate filters

### Hibernate @Filter mechanics (Quarkus 3.35.2 / Hibernate 6.x)
- `@FilterDef(name = "userScope", parameters = @ParamDef(name = "userId", type = Long.class))` on each entity
- `@Filter(name = "userScope", condition = "user_id = :userId")` on each entity
- Filter enabled per-session: `session.enableFilter("userScope").setParameter("userId", userId)`
- Panache provides `getEntityManager()` on PanacheEntityBase — use `Session.class` unwrap to enable filters
- **Critical:** Panache static methods (`Entity.findById()`, `Entity.listAll()`) DO respect Hibernate session filters when they are enabled on the underlying Session, because Panache delegates to the Hibernate Session

### Filter enablement strategy
Option A: Enable in each repository method (repetitive but explicit)
Option B: JAX-RS `@Provider` ContainerRequestFilter that enables filters before any resource method runs
**Recommendation: Option B** — a single `@Provider` `ContainerRequestFilter` that:
1. Reads `X-WorkOS-User-Id` header
2. Resolves/provisions local user
3. Enables `userScope` filter on the Hibernate Session
4. Stores userId in `UserContext` bean
This ensures every query path is covered. The public endpoint skips filter enablement (no header present → no filter enabled → unscoped queries for public).

Wait — this creates a problem: if no filter is enabled and someone hits a non-public endpoint without the header, queries return ALL users' data. The ContainerRequestFilter must enforce: non-public paths MUST have the header, or return 401.

### Flyway migration plan (V10)
Single migration with multiple statements:
1. `ALTER TABLE app_user ADD COLUMN workos_id VARCHAR(255) UNIQUE`
2. `ALTER TABLE category ADD COLUMN user_id BIGINT REFERENCES app_user(id)`
3. `ALTER TABLE contact ADD COLUMN user_id BIGINT REFERENCES app_user(id)`
4. `ALTER TABLE transaction ADD COLUMN user_id BIGINT REFERENCES app_user(id)`
5. `ALTER TABLE subscription ADD COLUMN user_id BIGINT REFERENCES app_user(id)`
6. `ALTER TABLE debt ADD COLUMN user_id BIGINT REFERENCES app_user(id)`
7. Backfill: `UPDATE category SET user_id = 1` (and same for contact, transaction, subscription, debt)
8. Add NOT NULL: `ALTER TABLE category ALTER COLUMN user_id SET NOT NULL` (and same for all)
9. Drop old unique: `ALTER TABLE category DROP CONSTRAINT category_name_key`
10. Add compound unique: `ALTER TABLE category ADD CONSTRAINT category_user_name_unique UNIQUE(user_id, name)`

### Domain model changes
Domain POJOs gain `userId` field: Transaction, Category, Contact, Subscription, Debt.
Port interfaces (TransactionRepository, etc.) — no signature changes needed if filters handle scoping transparently. However, `save()` methods need to set user_id on the entity, which means the service layer must pass userId.

Two approaches:
- A) Add userId parameter to repository save/update methods
- B) Set user_id in the entity from UserContext in each repository's `toEntity()`

**Recommendation: B** — inject `UserContext` into each Panache repository. The repository reads the current user from `UserContext` and sets `entity.user` on save/update. Domain model stays clean (no userId in domain POJOs). Port interfaces unchanged.

Actually, reconsidering — the domain model already has `Long` IDs for related entities (e.g., Transaction has `categoryId`). Following the same pattern, adding `userId` to domain models would be consistent. But since we're scoping transparently via filters, the domain layer doesn't need to know about userId for reads. For writes, the repository can inject UserContext.

**Final recommendation: Don't add userId to domain POJOs.** Keep scoping entirely in infrastructure. Repository `toEntity()` methods inject UserContext for writes. Filters handle reads.

## Natural Seams (task boundaries)

1. **Migration V10** — Add columns, backfill, constraints. Pure SQL, independently testable.
2. **UserEntity + workos_id** — Add column to entity, add `findByWorkosId()` method.
3. **UserContext + ContainerRequestFilter** — New CDI bean + JAX-RS filter. Resolves/provisions user, enables Hibernate filters.
4. **Entity @Filter annotations** — Add `@FilterDef` and `@Filter` to all 5 direct-ownership entities (Category, Contact, Transaction, Subscription, Debt). Add `@ManyToOne` user FK to each.
5. **Repository updates** — Inject UserContext, set user on toEntity(), add WHERE user_id to 3 native queries.
6. **SvelteKit proxy header injection** — Add `X-WorkOS-User-Id` to proxy, skip for public paths.
7. **Integration verification** — Two-user test proving isolation.

## First Proof (highest risk, build first)

**Task 3 (UserContext + ContainerRequestFilter) + Task 4 (Entity @Filter)** together — this is the core mechanism. If Hibernate @Filter doesn't work with Panache static methods, we need to know immediately and pivot to explicit WHERE clauses. Build these first, verify with a single entity (e.g., Category), then expand.

## Risks and Constraints

### Hibernate @Filter + Panache interplay (HIGH)
Panache's `PanacheEntityBase.listAll()` and `PanacheEntityBase.find()` internally use the Hibernate Session. Filters enabled on that Session should apply to all queries from that Session. However, this must be verified — Panache's compile-time bytecode enhancement could potentially bypass the Session query pipeline. **Mitigation:** Build Task 3+4 first with CategoryEntity, run `listAll()` with filter enabled, verify only user's categories returned.

### Native SQL queries bypass filters (MEDIUM)
3 native queries must be manually updated. Miss one = data leak. All 3 are in the aggregation path (dashboard summaries, debt balances).

### Category UNIQUE constraint (LOW)
Current: `category.name UNIQUE` globally. After multi-user: `UNIQUE(user_id, name)` — each user can have their own "Groceries". Migration must drop old constraint before adding new one. The `type IN ('INGRESS', 'EGRESS', 'BOTH')` CHECK constraint stays as-is.

### Public endpoint filter bypass (MEDIUM)
`PublicSubscriptionResource` queries subscription by token_uuid. If the userScope filter is enabled by the ContainerRequestFilter, the public endpoint won't find subscriptions belonging to other users. Solution: ContainerRequestFilter skips filter enablement when no `X-WorkOS-User-Id` header is present (public paths go through SvelteKit with session=null → no header → no filter). This is safe because the proxy doesn't add the header for public paths.

### SubscriptionBillingScheduler (MEDIUM)
`SubscriptionBillingScheduler` is a `@Scheduled` job that runs without an HTTP request context — no `X-WorkOS-User-Id` header, no UserContext. This scheduler finds subscriptions due for billing and creates payment records. After multi-user, it must iterate ALL users' subscriptions (no filter). Since there's no request context, the Hibernate filter won't be enabled, so it naturally queries all data. **This is correct behavior** — the scheduler is a system-level operation.

### JIT provisioning race condition (LOW)
Two simultaneous first requests from the same WorkOS user could attempt to create two local records. Use `INSERT ... ON CONFLICT (workos_id) DO NOTHING` or catch the unique constraint violation and retry the lookup.

## Verification

- `./mvnw quarkus:dev` — app starts without migration errors
- Create two users in WorkOS dashboard, log in as each
- As user A: create a category "Test A"
- As user B: create a category "Test B"
- As user A: GET /api/categories → only "Test A" visible
- As user B: GET /api/categories → only "Test B" visible
- Dashboard summaries show per-user data only
- Public subscription page still works (no filter applied)
- Scheduler still runs (no filter applied)

## Skill Discovery

Technologies in play: Quarkus 3.35, Hibernate ORM 6.x, Panache, Flyway, SvelteKit, WorkOS.
- `svelte-code-writer` and `svelte-core-bestpractices` — already installed, relevant for proxy changes
- No professional agent skills needed for Quarkus/Hibernate/Flyway — these are well-understood patterns in the codebase

## Sources

- Hibernate ORM 6.x @FilterDef/@Filter documentation (standard Hibernate feature, stable API)
- Quarkus Hibernate ORM + Panache guide (Panache delegates to Hibernate Session)
- WorkOS User Management API (user.id field from session)
