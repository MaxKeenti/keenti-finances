# S07: Public Subscription View — UAT

**Milestone:** M001
**Written:** 2026-05-14T20:39:17.917Z

# S07: Public Subscription View — UAT

**Milestone:** M001
**Written:** 2026-05-14

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: S07 proof level is "contract — static compilation + file assertions"; full E2E deferred to S08 deployment. All backend and frontend artifacts are verifiable at rest.

## Preconditions

- Quarkus backend compiles cleanly (`./mvnw compile -q` from `backend/` exits 0)
- SvelteKit frontend has no new type errors in S07 files (`bun run check` shows 0 errors for public route files)
- Files exist: `PublicSubscriptionResource.java`, `+page.server.ts`, `+page.svelte`
- `hooks.server.ts` contains the `/public` bypass

## Smoke Test

Run: `grep -q '/public' frontend/src/hooks.server.ts && echo PASS` — should print PASS, confirming the auth guard bypass is in place.

## Test Cases

### 1. Auth bypass is configured

1. Open `frontend/src/hooks.server.ts`
2. Locate the `PUBLIC_PATHS` array (or equivalent bypass list)
3. **Expected:** `'/public'` (or `"/public"`) is present, ensuring all `/public/*` routes bypass authentication without per-route changes

### 2. Backend endpoint is wired correctly

1. Open `PublicSubscriptionResource.java`
2. Verify the class has `@Path` containing `/api/public/subscriptions` and a `@GET @Path("{token}")` method
3. Verify no `@RolesAllowed` or `@Authenticated` annotations are present
4. **Expected:** Endpoint is reachable without auth; returns 200 with JSON payload for valid token, 404 for invalid token

### 3. Backend token lookup method exists

1. Open `SubscriptionRepository.java` (port interface)
2. **Expected:** Method signature `findByTokenUuid(String tokenUuid)` or equivalent is present

### 4. Use case port has getByToken

1. Open `SubscriptionUseCase.java`
2. **Expected:** Method signature `getByToken(String token)` or equivalent is present

### 5. SvelteKit route files exist at correct paths

1. Confirm file exists: `frontend/src/routes/public/subscription/[token]/+page.server.ts`
2. Confirm file exists: `frontend/src/routes/public/subscription/[token]/+page.svelte`
3. **Expected:** Both files present; no syntax or type errors reported by svelte-check for these files

### 6. Frontend load function handles 404

1. Open `+page.server.ts`
2. Locate the load function
3. **Expected:** On a non-200 response from the backend, the load function calls `error(404, ...)` or equivalent SvelteKit 404 throw

### 7. Frontend page renders member payment data

1. Open `+page.svelte`
2. **Expected:** Page renders subscription name, member names, and payment status grouped by billing period without requiring auth

## Edge Cases

### Invalid token returns 404

1. In `PublicSubscriptionResource.java`, verify the not-found branch returns `Response.status(404)` with a structured JSON error body
2. **Expected:** Same error shape as other 404 responses in the codebase; token-not-found is also logged at INFO

### Public path prefix covers future routes

1. In `hooks.server.ts`, verify the bypass uses prefix matching (e.g., `startsWith('/public')`) rather than an exact match
2. **Expected:** Any future route under `/public/*` automatically bypasses auth without additional configuration

## Failure Signals

- `grep '/public' hooks.server.ts` returns nothing — auth bypass missing, all /public routes will redirect to login
- `PublicSubscriptionResource.java` has `@RolesAllowed` or `@Authenticated` — endpoint will reject unauthenticated requests
- `findByTokenUuid` absent from SubscriptionRepository — runtime MethodNotFound at token lookup
- `+page.server.ts` missing — SvelteKit 404 on all public subscription URLs
- `./mvnw compile -q` exits non-zero — backend broken

## Not Proven By This UAT

- Live HTTP request to running Quarkus instance with a real token UUID
- Browser rendering of the public page with actual member and payment data
- Mobile Safari layout verification
- Performance under concurrent unauthenticated requests
- Token expiry or rotation behavior (not in scope for M001)

## Notes for Tester

Pre-existing svelte-check errors (70 errors in session.ts, login, categories pages) are baseline noise from prior slices — they are unrelated to S07 and should be ignored when evaluating S07 quality. The public page has no sidebar or navigation by design — it is intentionally minimal for external sharing.
