# S07: Public Subscription View

**Goal:** Open a UUID token link without login, see all members' payment status for that subscription; invalid tokens show 404
**Demo:** Open a UUID token link without login, see all members' payment status for that subscription; invalid tokens show 404

## Must-Haves

- 1. GET /api/public/subscriptions/{tokenUuid} returns subscription name, members with contact names, and payment records grouped by billing date — no auth required
- 2. SvelteKit route /public/subscription/[token] renders a read-only page showing subscription info, member list, and per-member payment status per billing period
- 3. Invalid/nonexistent token UUIDs return 404 on both backend and frontend
- 4. The /public/subscription/* path is excluded from the SvelteKit auth guard in hooks.server.ts
- 5. `./mvnw compile -q` exits 0; `bun run check` shows no new errors from S07 files

## Proof Level

- This slice proves: contract — static compilation + file assertions; full E2E deferred to S08 deployment

## Integration Closure

Upstream surfaces consumed: Subscription.tokenUuid (domain model), SubscriptionRepository (port), PaymentRecordUseCase.listBySubscription, SubscriptionUseCase.listMembers, ContactUseCase.getById, SvelteKit hooks.server.ts PUBLIC_PATHS. New wiring: PublicSubscriptionResource (unauthenticated REST endpoint), /public/subscription/[token] SvelteKit route. What remains: S08 (Railway deployment) for full end-to-end verification.

## Verification

- Public endpoint logs token lookups (found/not-found) via JBoss Logger. 404 responses return structured JSON error body consistent with existing patterns.

## Tasks

- [x] **T01: Add public subscription REST endpoint with token-based lookup** `est:45m`
  ## Description
  - Files: `backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java`, `backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java`
  - Verify: ./mvnw compile -q -f backend/pom.xml && grep -q 'findByTokenUuid' backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java && grep -q 'getByToken' backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java && test -f backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java

- [x] **T02: Build SvelteKit /public/subscription/[token] read-only page and add auth bypass** `est:45m`
  ## Description
  - Files: `frontend/src/hooks.server.ts`, `frontend/src/routes/public/subscription/[token]/+page.server.ts`, `frontend/src/routes/public/subscription/[token]/+page.svelte`
  - Verify: grep -q '/public' frontend/src/hooks.server.ts && test -f frontend/src/routes/public/subscription/\[token\]/+page.server.ts && test -f frontend/src/routes/public/subscription/\[token\]/+page.svelte

## Files Likely Touched

- backend/src/main/java/com/keenti/finances/domain/port/out/SubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheSubscriptionRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/in/SubscriptionUseCase.java
- backend/src/main/java/com/keenti/finances/application/service/SubscriptionService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResponse.java
- frontend/src/hooks.server.ts
- frontend/src/routes/public/subscription/[token]/+page.server.ts
- frontend/src/routes/public/subscription/[token]/+page.svelte
