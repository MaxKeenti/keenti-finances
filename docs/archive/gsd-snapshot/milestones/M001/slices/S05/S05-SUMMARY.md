---
id: S05
parent: M001
milestone: M001
provides:
  - Subscription domain model with PERSONAL/SHARED types and UUID token
  - SubscriptionMember and PaymentRecord domain models with PENDING/PAID status
  - Daily billing scheduler generating 7-day-lead PENDING payment records
  - REST API: /api/subscriptions (CRUD), /api/subscriptions/{id}/members (POST/GET/DELETE), /api/subscriptions/{id}/payments (GET/PUT)
  - SvelteKit /subscriptions (list/create/edit/delete) and /subscriptions/[id] (members, payment recording, token copy)
  - UUID token_uuid per SHARED subscription — consumed by S07 public view
requires:
  - slice: S01
    provides: auth middleware, proxy pattern, app-shell
  - slice: S02
    provides: contact domain model for member assignment
affects:
  []
key_files:
  - backend/src/main/resources/db/migration/V4__create_subscription_tables.sql
  - backend/src/main/java/com/keenti/finances/domain/model/Subscription.java
  - backend/src/main/java/com/keenti/finances/domain/model/SubscriptionMember.java
  - backend/src/main/java/com/keenti/finances/domain/model/PaymentRecord.java
  - backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java
  - frontend/src/routes/subscriptions/+page.svelte
  - frontend/src/routes/subscriptions/+page.server.ts
  - frontend/src/routes/subscriptions/[id]/+page.svelte
  - frontend/src/routes/subscriptions/[id]/+page.server.ts
key_decisions:
  - PaymentRecordResource as sibling @Path class (not JAX-RS sub-resource) to avoid locator complexity
  - Billing scheduler cron 0 0 1 * * ? (1am) to avoid midnight boundary edge cases
  - UUID token generated at create-time for SHARED; cleared on SHARED→PERSONAL; regenerated on PERSONAL→SHARED
  - Member add to PERSONAL subscription throws 400; duplicate member throws 409
  - All form actions (addMember, removeMember, recordPayment) use plain formData — no superforms — consistent with S02/S03 delete pattern
  - paymentsByDate grouped with $derived rune, dates sorted descending (newest billing period first)
patterns_established:
  - Sibling @Path resource for nested endpoints with multiple verbs (avoids JAX-RS sub-resource locator)
  - Plain formData actions for mutating operations (add/remove member, record payment) — same as delete pattern
  - Load members server-side for all SHARED subs to avoid client-side API calls
observability_surfaces:
  - JBoss Logger structured lines on subscription/member/payment CRUD operations
  - Scheduler logs each run with count of payment records generated
  - REST resources return 400/404/409 structured JSON error bodies
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-14T13:09:27.652Z
blocker_discovered: false
---

# S05: Subscription Management

**Full subscription management: personal and shared subscriptions with member assignment, daily billing scheduler generating PENDING payment records, and payment recording UI — all wired end-to-end through hexagonal backend and SvelteKit.**

## What Happened

S05 delivered subscription management across three tasks spanning the full stack.

**T01 — Hexagonal backend + Flyway V4 migration + scheduler**
Added Flyway V4 migration creating `subscription`, `subscription_member`, and `payment_record` tables. Built the full hexagonal stack: domain models (`Subscription`, `SubscriptionMember`, `PaymentRecord`) with no framework imports, use-case ports (`SubscriptionUseCase`, `PaymentRecordUseCase`), repository ports, Panache persistence adapters, application services (`SubscriptionService`, `PaymentRecordService`, `SubscriptionBillingScheduler`), and REST resources (`SubscriptionResource`, `PaymentRecordResource`).

Key decisions: `PaymentRecordResource` is a sibling `@Path` class rather than a JAX-RS sub-resource to avoid locator complexity. The billing scheduler runs at 1am daily (`0 0 1 * * ?`) to avoid midnight boundary edge cases. UUID token is generated at creation time for SHARED subscriptions; cleared when type changes to PERSONAL, regenerated when switching back to SHARED. Members added to PERSONAL subscriptions throw 400; duplicates throw 409.

**T02 — SvelteKit /subscriptions CRUD page**
Built `/subscriptions` with a card grid listing all subscriptions. Create/edit dialog supports subscription type toggle (PERSONAL/SHARED), amount, billing date, and category. Shared subscriptions show member-assignment UI drawing from the contacts dropdown. Add/remove member actions use plain formData (consistent with the delete pattern from S02/S03). Members fetched on page load for all SHARED subs to populate member count without extra client-side calls. Sidebar and bottom-nav already contained subscription nav items from prior slices — no edit needed.

**T03 — SvelteKit /subscriptions/[id] detail page**
Built `/subscriptions/[id]` showing member list, payment records grouped by billing date (newest-first via `$derived` rune), PENDING→PAID recording per member, and UUID token copy button for SHARED subscriptions. `memberId null` maps to 'Owner' label for personal subscriptions. `recordPayment` uses plain formData action consistent with T01/T02 conventions.

## Verification

1. `./mvnw compile -q` (backend/pom.xml): exit 0 — COMPILE_OK. Confirmed with gsd_exec run d956f406.
2. Domain model cleanliness: grep confirms no `jakarta` or `io.quarkus` imports in Subscription.java, SubscriptionMember.java, PaymentRecord.java.
3. V4 migration file exists at `backend/src/main/resources/db/migration/V4__create_subscription_tables.sql`.
4. All REST and service files confirmed present: SubscriptionResource.java, PaymentRecordResponse.java, SubscriptionBillingScheduler.java (with @Scheduled cron "0 0 1 * * ?"), domain models.
5. `bun run check` (main project frontend): 10180 pre-existing errors from node_modules/effect/src SubscriptionRef — zero errors from subscription route files. grep of check output confirms all "subscription" matches originate from Effect library, not from our pages.
6. Static file assertions (gsd_exec run f6d24549): subscriptions/+page.svelte OK, subscriptions/+page.server.ts OK, subscriptions/[id]/+page.svelte OK, subscriptions/[id]/+page.server.ts OK, sidebar OK, bottom-nav OK.
7. tokenUuid field present in Subscription.java; SHARED/PERSONAL type branching confirmed via grep.

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

PaymentRecordResource implemented as a sibling @Path class rather than a JAX-RS sub-resource of SubscriptionResource. REST contract is identical; change avoids sub-resource locator complexity.

## Known Limitations

Scheduler 7-day lead generation logic requires a running Quarkus instance — not integration-tested statically. Full E2E payment flow verification deferred to S08 deployment.

## Follow-ups

S07 (Public Subscription View) can now consume tokenUuid and payment status data. S06 (Debt Tracking) proceeds independently via S03.

## Files Created/Modified

None.
