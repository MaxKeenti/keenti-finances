# S01: Multi-user data foundation — UAT

**Milestone:** M003
**Written:** 2026-05-23T18:49:06.706Z

## UAT Type
Contract verification (compilation + test suite). Runtime integration (two-user data isolation) requires a running dev server with live WorkOS; that path is marked **Not Proven By This UAT**.

## Preconditions
- Quarkus running with dev-services PostgreSQL (or H2 for test profile)
- Two distinct WorkOS user ID strings available (e.g. `user_A`, `user_B`)
- SvelteKit frontend reachable and WorkOS session configured

## Scenario 1: Schema migration applies cleanly
1. Start Quarkus in dev/test mode
2. Observe Flyway output in startup logs

**Expected:** V10 migration applies without error; `category`, `contact`, `transaction`, `subscription`, `debt` tables each have a `user_id` column (NOT NULL, FK to `app_user`); pre-existing rows have `user_id = 1`.

## Scenario 2: Unauthenticated request is blocked
1. `curl -s -o /dev/null -w "%{http_code}" http://localhost:8080/api/categories` (no header)

**Expected:** HTTP 401. Log line: `auth.workos.header.missing path=/api/categories`.

## Scenario 3: JIT provisioning on first login
1. Send `GET /api/categories` with `X-WorkOS-User-Id: new-user-xyz` (a WorkOS ID not yet in the DB)

**Expected:** Log shows `auth.workos.jit_provisioned userId=<n> workosId=new-user-xyz`. A row appears in `app_user` with `workos_id = 'new-user-xyz'`. Second request with same header shows `auth.workos.scope.enabled` only (no re-provisioning).

## Scenario 4: Row-level isolation between two users
1. `POST /api/categories` with `X-WorkOS-User-Id: user_A` — body `{name:"A's Category", type:"INGRESS"}`
2. `POST /api/categories` with `X-WorkOS-User-Id: user_B` — body `{name:"B's Category", type:"EGRESS"}`
3. `GET /api/categories` with `X-WorkOS-User-Id: user_A`

**Expected step 3:** Response contains only "A's Category"; "B's Category" is absent.

4. `GET /api/categories` with `X-WorkOS-User-Id: user_B`

**Expected step 4:** Response contains only "B's Category"; "A's Category" is absent.

## Scenario 5: SvelteKit header injection
1. Log in via WorkOS as user A in the browser
2. Navigate to any data page (categories, transactions)
3. Open DevTools → Network; inspect any `/api/*` request

**Expected:** Request headers include `X-WorkOS-User-Id: <workos_id_of_user_A>`. Data returned belongs exclusively to user A.

## Edge Cases
- **JIT race condition:** Two simultaneous first-login requests for the same `workos_id` — unique constraint violation is caught; only one `app_user` row is inserted.
- **Existing data integrity:** Pre-migration rows continue to be accessible under user 1; no data loss.
- **Public paths:** Requests to non-auth endpoints (e.g., public subscription landing pages) succeed without `X-WorkOS-User-Id` because `UserScopeFilter` does not block those routes.

## Not Proven By This UAT
- Actual WorkOS OAuth flow end-to-end (requires live WorkOS tenant and browser session)
- Runtime cross-entity isolation for transactions, contacts, subscriptions, debts (structurally guaranteed by identical @Filter pattern; tested at service level via category tests)
- S02 soft-delete filter stacking on top of userScope filter
- Concurrent multi-user load / race condition stress testing
