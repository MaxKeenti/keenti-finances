# S05: Subscription Management

**Goal:** Create personal and shared subscriptions with member assignment from contacts, run a daily scheduler that generates upcoming PENDING payment records (7-day lead), and record payments per member per period. Shared subscriptions carry a UUID token for the S07 public view.
**Demo:** Create personal and shared subscriptions, assign members, see scheduler-generated upcoming payment records, record payments per member

## Must-Haves

- 1. `./mvnw compile -q` exits 0 with subscription domain, scheduler, and REST endpoints compiling clean
- 2. `bun run check` exits 0 for all SvelteKit subscription pages
- 3. Subscription CRUD works at /api/subscriptions (POST, GET, PUT, DELETE)
- 4. Member assignment works at /api/subscriptions/{id}/members (POST, GET, DELETE)
- 5. Payment record listing and recording works at /api/subscriptions/{id}/payments (GET, PUT)
- 6. Scheduler generates PENDING payment records for subscriptions with nextBillingDate within 7 days
- 7. Shared subscriptions auto-generate a UUID token on creation
- 8. SvelteKit /subscriptions page lists, creates, edits, and deletes subscriptions
- 9. SvelteKit /subscriptions/[id] page shows members, payment records, and allows payment recording
- 10. Nav items for Subscriptions appear in sidebar and bottom-nav

## Proof Level

- This slice proves: integration

## Integration Closure

Upstream surfaces consumed: Contact domain model and repository (S02), Category domain model (S02), auth middleware and proxy pattern (S01), app-shell sidebar/bottom-nav (S01/S02). New wiring: quarkus-scheduler dependency, daily billing job, /subscriptions and /subscriptions/[id] routes. What remains: S06 (debts), S07 (public view consuming token_uuid and payment data), S08 (deployment).

## Verification

- Structured JBoss Logger lines on subscription/member/payment CRUD operations. Scheduler logs each run with count of payment records generated. REST resources return structured JSON error bodies (400/404/409). SvelteKit surfaces success/failure via sonner toast notifications. Payment record status (PENDING/PAID) visible in DB and UI.

## Tasks

- [x] **T01: Flyway V4 migration + full hexagonal subscription backend with scheduler** `est:2h`
  ## Description
  - Files: `backend/pom.xml`, `backend/src/main/resources/db/migration/V4__create_subscription_tables.sql`, `backend/src/main/resources/application.properties`, `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java`, `backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java`, `backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/PaymentRecordUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionMemberRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/PaymentRecordRepository.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`, `backend/src/main/java/com/keenti/finances/application/service/PaymentRecordService.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionMemberEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PaymentRecordEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionMemberRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanachePaymentRecordRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java`
  - Verify: ./mvnw compile -q && ! grep -rq 'import jakarta\|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/model/Subscription.java backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java && test -f backend/src/main/resources/db/migration/V4__create_subscription_tables.sql

- [x] **T02: Build SvelteKit /subscriptions CRUD page with member assignment** `est:1h30m`
  ## Description
  - Files: `frontend/src/routes/subscriptions/+page.server.ts`, `frontend/src/routes/subscriptions/+page.svelte`, `frontend/src/lib/components/app-shell/sidebar.svelte`, `frontend/src/lib/components/app-shell/bottom-nav.svelte`
  - Verify: bun run check && test -f frontend/src/routes/subscriptions/+page.svelte && test -f frontend/src/routes/subscriptions/+page.server.ts && grep -q 'subscriptions' frontend/src/lib/components/app-shell/sidebar.svelte && grep -q 'subscriptions' frontend/src/lib/components/app-shell/bottom-nav.svelte

- [x] **T03: Build SvelteKit /subscriptions/[id] detail page with payment recording** `est:1h30m`
  ## Description
  - Files: `frontend/src/routes/subscriptions/[id]/+page.server.ts`, `frontend/src/routes/subscriptions/[id]/+page.svelte`, `frontend/src/routes/subscriptions/+page.svelte`
  - Verify: bun run check && test -f 'frontend/src/routes/subscriptions/[id]/+page.svelte' && test -f 'frontend/src/routes/subscriptions/[id]/+page.server.ts'

## Files Likely Touched

- backend/pom.xml
- backend/src/main/resources/db/migration/V4__create_subscription_tables.sql
- backend/src/main/resources/application.properties
- backend/src/main/java/com/keenti/finances/domain/model/Subscription.java
- backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java
- backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java
- backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/in/PaymentRecordUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionMemberRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/PaymentRecordRepository.java
- backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
- backend/src/main/java/com/keenti/finances/application/service/PaymentRecordService.java
- backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/SubscriptionMemberEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PaymentRecordEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionMemberRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanachePaymentRecordRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java
- frontend/src/routes/subscriptions/+page.server.ts
- frontend/src/routes/subscriptions/+page.svelte
- frontend/src/lib/components/app-shell/sidebar.svelte
- frontend/src/lib/components/app-shell/bottom-nav.svelte
- frontend/src/routes/subscriptions/[id]/+page.server.ts
- frontend/src/routes/subscriptions/[id]/+page.svelte
