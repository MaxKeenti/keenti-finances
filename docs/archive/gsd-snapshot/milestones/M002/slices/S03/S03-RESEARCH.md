# S03 Research: Subscription Model Improvements

**Depth:** Targeted research — established backend patterns (Quarkus REST, Panache, Flyway) with moderately complex business logic changes and new frontend interactions.

## Summary

S03 adds three capabilities to the subscription system: (1) owner participation toggle changing billing split math, (2) manual billing trigger reusing scheduler logic, (3) transaction-subscription linking with inline previews. The backend patterns are well-established — CRUD resources, Panache repositories, Flyway migrations. The billing scheduler already has the core logic; it just needs extraction into a reusable method. The frontend subscription pages exist with dialogs and form handling via sveltekit-superforms.

## Recommendation

1. Start with Flyway migration (owner_participates + subscription_id FK) — unblocks all backend work
2. Fix billing split math (touches SubscriptionService member calculation + scheduler)
3. Manual billing endpoint (reuses scheduler, low risk)
4. Transaction linking endpoint (new but follows existing resource pattern)
5. Frontend: toggle + button + linking UI

## Implementation Landscape

### Backend: Database Migration

**Next version:** V7 (or V8 if S02 takes V7 — coordinate)

**Migration needed:**
```sql
ALTER TABLE subscription ADD COLUMN owner_participates BOOLEAN NOT NULL DEFAULT true;
ALTER TABLE transaction ADD COLUMN subscription_id BIGINT REFERENCES subscription(id);
```

### Backend: Owner Participation Toggle

**Current split math** in `SubscriptionService.java` (lines 113-114):
```java
int newCount = existing.size() + 1;  // members only
BigDecimal share = sub.getCost().divide(BigDecimal.valueOf(newCount), 2, RoundingMode.HALF_UP);
```

**New math:**
```java
int divisor = memberCount + (sub.isOwnerParticipates() ? 1 : 0);
BigDecimal share = sub.getCost().divide(BigDecimal.valueOf(divisor), 2, RoundingMode.HALF_UP);
```

**Files to modify:**
| File | Change |
|------|--------|
| `domain/model/Subscription.java` | Add `boolean ownerParticipates` field (default true) |
| `persistence/SubscriptionEntity.java` | Add `@Column owner_participates` |
| `persistence/PanacheSubscriptionRepository.java` | Update mappers |
| `rest/SubscriptionRequest.java` | Add `Boolean ownerParticipates` (default true if null) |
| `rest/SubscriptionResponse.java` | Add `boolean ownerParticipates` |
| `service/SubscriptionService.java` | Fix split divisor in addMember/removeMember |

**Impact on existing members:** When owner_participates is toggled, share amounts for all existing members must be recalculated. Add a recalculation in the update flow when this field changes.

### Backend: Manual Billing Trigger

**Current scheduler** (`SubscriptionBillingScheduler.java` lines 36-76):
- `@Scheduled(cron = "0 0 1 * * ?")` — daily at 1 AM
- Finds subscriptions due within 7 days
- Creates PaymentRecord per member (SHARED) or single record (PERSONAL)
- Advances nextBillingDate

**Approach:** Extract the core logic from the scheduled method into a service method. The manual trigger endpoint calls the same method. Idempotent by design (scheduler checks for existing records).

**New endpoint:** `POST /api/subscriptions/generate-billing`
- Returns: `{ generated: int }` (count of new payment records)
- No request body needed (processes all eligible subscriptions)

**New file:** `BillingResource.java` (or add to existing `SubscriptionResource.java`)

### Backend: Transaction-Subscription Linking

**Current Transaction model** (`Transaction.java`):
```java
private Long id, categoryId, contactId;
private BigDecimal amount;
private String direction, description;
private LocalDate transactionDate;
```

**Add:** `Long subscriptionId` (nullable FK)

**New endpoint:** `PUT /api/transactions/{id}/link-subscription`
- Request body: `{ subscriptionId: Long }` (null to unlink)
- Atomic — single row update
- Returns updated transaction

**For bulk linking from subscription detail page:**
`POST /api/subscriptions/{id}/link-transactions`
- Request body: `{ transactionIds: Long[] }`
- Atomic — all or nothing (single transaction)
- Returns count linked

**Files to modify:**
| File | Change |
|------|--------|
| `domain/model/Transaction.java` | Add `Long subscriptionId` |
| `persistence/TransactionEntity.java` | Add `@Column subscription_id` + `@ManyToOne` |
| `persistence/PanacheTransactionRepository.java` | Update mappers |
| `rest/TransactionResource.java` | Add link endpoint |
| `rest/TransactionResponse.java` | Add `subscriptionId` |

### Frontend: Owner Participation Toggle

**Location:** `frontend/src/routes/subscriptions/+page.svelte` (create/edit dialog)

Add a Switch/Toggle component after the type select, visible only when type === 'SHARED':
- Label: "I participate in this subscription"
- Default: checked (true)
- Bound to form field `ownerParticipates`

**Also show on detail page** (`frontend/src/routes/subscriptions/[id]/+page.svelte`):
- Display current participation mode in subscription header
- Allow toggling from detail page (triggers recalculation)

### Frontend: Generate Billing Button

**Location:** `frontend/src/routes/subscriptions/+page.svelte` or `[id]/+page.svelte`

Per architectural decision: button on subscriptions **list** page (not per-subscription).
- Button text: "Generate Billing"
- Action: POST to `/api/subscriptions/generate-billing`
- Success toast: "Generated X payment records"
- Error toast on failure

### Frontend: Transaction Linking

**Location:** `frontend/src/routes/subscriptions/[id]/+page.svelte`

New section on subscription detail page:
- "Linked Transactions" list showing already-linked transactions
- "Link Transactions" button opens multi-select dialog
- Dialog shows unlinked transactions with inline previews (amount, date, description, category badge)
- Confirm links all selected atomically

**Data flow:** Need to fetch unlinked transactions (`GET /api/transactions?unlinked=true` or similar filter) and display with previews.

### API Proxy

SvelteKit server-side code calls backend directly via `BACKEND_URL` env var (default `http://localhost:8080`). No browser-side API calls. New endpoints follow the same pattern — call from `+page.server.ts` actions.

## Natural Seams (Task Decomposition)

1. **Migration + backend models** — V7/V8 migration, Subscription + Transaction domain/entity/DTO changes
2. **Billing split math fix** — SubscriptionService addMember/removeMember + update flow recalculation
3. **Manual billing endpoint** — Extract scheduler logic, new resource endpoint
4. **Transaction linking endpoint** — New endpoint following existing resource pattern
5. **Frontend: owner toggle + generate button** — Form changes + action
6. **Frontend: transaction linking UI** — Multi-select dialog with previews

## First Proof (Highest Risk / Biggest Unblocker)

The billing split math change. It affects existing member share calculations and has correctness implications for financial data. If divisor logic is wrong, all payment records will have incorrect amounts. Unit test this calculation thoroughly.

## Verification

- `./mvnw compile` — backend compiles
- `./mvnw test` — existing tests pass (if any)
- `npx vite build` — frontend builds
- `npx svelte-check --threshold error` — no new errors
- Manual test: create SHARED subscription, toggle owner_participates, verify share amounts change
- Manual test: trigger billing, verify payment records created
- Manual test: link transactions, verify FK persisted

## Constraints

- Migration version must coordinate with S02 (both need V7+)
- Manual billing is global (all eligible subscriptions), not per-subscription
- Transaction linking is atomic — partial linking is not acceptable
- Owner participation toggle must trigger share recalculation for all existing members
- Billing scheduler already checks for existing records (idempotent) — manual trigger inherits this
