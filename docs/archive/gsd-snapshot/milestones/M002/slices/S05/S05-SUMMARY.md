---
id: S05
parent: M002
milestone: M002
provides:
  - WorkOS OAuth/PKCE auth flow
  - Encrypted session cookie infrastructure
  - Passkey-only login gate on all protected routes
requires:
  []
affects:
  []
key_files: []
key_decisions:
  - Used @workos-inc/node directly (no authkit-sveltekit — does not exist on npm)
  - AES-256-GCM with scrypt-derived key via node:crypto — avoids iron-session dependency
  - WorkOS client is a lazy singleton (getWorkOS()) — eager init throws during vite build when env vars absent
  - JWT expiry checked by base64url-decoding payload — avoids jwt-decode dependency
  - Migration is V9 (V7 already taken by color migration)
  - Logout redirects to /login, not / — avoids immediate re-auth redirect loop
patterns_established:
  - Lazy singleton pattern for WorkOS client: getWorkOS() checks instance before creating — use for any SDK client that reads env vars at init time
  - AES-256-GCM session sealing pattern in workos-session.ts — reuse for any encrypted cookie need
  - PUBLIC_PATHS list in hooks.server.ts — extend when adding unauthenticated routes
observability_surfaces:
  - none
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-17T23:25:28.527Z
blocker_discovered: false
---

# S05: Passkey Auth via WorkOS

**Password auth replaced with passkey-only login via WorkOS AuthKit; full OAuth/PKCE flow wired in SvelteKit hooks and routes; backend auth dead code removed.**

## What Happened

T01 installed @workos-inc/node and created the WorkOS client singleton (lazy-initialized to survive vite build-time evaluation) plus AES-256-GCM session cookie utilities (sealSession/unsealSession using node:crypto with a scrypt-derived key). App.d.ts was updated to the WorkOS user shape.

T02 rewrote hooks.server.ts: skips public paths, detects JWT expiry by base64-decoding the access token payload, attempts refresh via authenticateWithRefreshToken (clears session and redirects to WorkOS on failure), and redirects unauthenticated requests to WorkOS authorization URL. Created /callback/+server.ts to exchange the OAuth code for a session token and set the encrypted cookie.

T03 completed the login flow: login/+page.server.ts now redirects immediately to WorkOS (password form eliminated); logout/+page.server.ts redirects to /login to avoid a re-auth loop; the old HMAC session.ts was deleted. A vite build failure was discovered — the WorkOS singleton was eager and threw at build-time when WORKOS_API_KEY was absent. Fixed by converting to a lazy getter (getWorkOS()) throughout.

T04 deleted the 5 dead backend auth files (AuthResource, AuthService, BcryptPasswordHasher, AuthUseCase, PasswordHasher), made password_hash nullable in User/UserEntity, and added V9__make_password_hash_nullable.sql. V9 was used because V7 was already taken by the color migration. Backend compiled cleanly with no dangling references.

## Verification

T01: svelte-check 0 errors + WorkOS import verified via node -e. T02: svelte-check 0 errors after svelte-kit sync. T03: svelte-check 0 errors + vite build exits 0. T04: ./mvnw compile -q exits 0 + grep confirms no remaining references to deleted auth classes.

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

None.

## Known Limitations

None.

## Follow-ups

S06: deferred fixes and backend tests. S07: Railway deployment with WorkOS env vars (WORKOS_API_KEY, WORKOS_CLIENT_ID, WORKOS_COOKIE_PASSWORD, PUBLIC_APP_URL) configured.

## Files Created/Modified

- `frontend/package.json` — Added @workos-inc/node dependency
- `frontend/src/lib/server/workos.ts` — WorkOS lazy singleton and getAuthorizationUrl helper
- `frontend/src/lib/server/workos-session.ts` — AES-256-GCM session cookie seal/unseal utilities
- `frontend/src/app.d.ts` — Updated session type to WorkOS user shape
- `frontend/src/hooks.server.ts` — Full WorkOS auth flow: public path skip, JWT expiry check, refresh, redirect
- `frontend/src/routes/callback/+server.ts` — OAuth code exchange and session cookie set
- `frontend/src/routes/logout/+page.server.ts` — Clear WorkOS session and redirect to /login
- `frontend/src/routes/login/+page.server.ts` — Redirect to WorkOS; no more password form
- `frontend/src/routes/login/+page.svelte` — Minimal redirecting placeholder
- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — Added two-arg constructor for WorkOS users (no password)
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — Made password_hash nullable
- `backend/src/main/resources/db/migration/V9__make_password_hash_nullable.sql` — DROP NOT NULL on password_hash column
