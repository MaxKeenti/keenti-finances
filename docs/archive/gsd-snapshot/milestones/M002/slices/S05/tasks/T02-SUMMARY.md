---
id: T02
parent: S05
milestone: M002
key_files:
  - frontend/src/hooks.server.ts
  - frontend/src/routes/callback/+server.ts
  - frontend/src/routes/logout/+page.server.ts
key_decisions:
  - JWT expiry checked by base64url-decoding the payload segment — avoids a jwt-decode dependency; 60-second lead prevents clock-skew races
  - Token refresh clears session and redirects to WorkOS on any failure — consistent with task failure-mode spec
  - Logout redirects to / (not /login); hooks then redirects to WorkOS — no dead /login page needed in the flow
  - PUBLIC_PATHS excludes /login — the old password login page is unreachable for unauthenticated users, which is the intended passkey-only behavior
duration: 
verification_result: passed
completed_at: 2026-05-17T22:47:48.208Z
blocker_discovered: false
---

# T02: Rewrote hooks.server.ts with WorkOS session validation and JWT refresh logic; created /callback OAuth code-exchange route; updated logout to use WorkOS session utilities

**Rewrote hooks.server.ts with WorkOS session validation and JWT refresh logic; created /callback OAuth code-exchange route; updated logout to use WorkOS session utilities**

## What Happened

hooks.server.ts was already partially updated by T01 (it imported from workos-session) but lacked WorkOS redirect, token refresh, and correct PUBLIC_PATHS. Rewrote it to: (1) skip public paths (/callback, /logout, /public, /health) without session; (2) detect expired access tokens by base64-decoding the JWT payload and checking `exp` with a 60-second lead; (3) attempt refresh via workos.userManagement.authenticateWithRefreshToken — on success update the cookie, on failure clear and redirect; (4) redirect unauthenticated requests to WorkOS authorization URL via getAuthorizationUrl(redirectUri). Created frontend/src/routes/callback/+server.ts GET handler: extracts `code` param, exchanges via workos.userManagement.authenticateWithCode(), seals session with setSession(), logs [workos-auth] session-create, redirects to /. Updated logout/+page.server.ts to use clearSession() from workos-session instead of the old HMAC session module, logging [workos-auth] logout. Ran svelte-kit sync to generate $types for the new callback route, then svelte-check --threshold error: 0 errors.

## Verification

Ran `cd frontend && npx svelte-check --threshold error` — exited 0 with 0 errors, 11 warnings (pre-existing). svelte-kit sync was required first to generate $types for the new callback route.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && npx svelte-kit sync && npx svelte-check --threshold error` | 0 | pass | 11000ms |

## Deviations

logout/+page.server.ts was updated (not in the task plan file list) because it imported the old HMAC session module and would have caused a runtime error or stale cookie on logout.

## Known Issues

login/+page.server.ts still imports from $lib/server/session (old HMAC module) — it compiles because session.ts still exists, but it is now unreachable via the new auth flow. Cleanup is expected in a subsequent task.

## Files Created/Modified

- `frontend/src/hooks.server.ts`
- `frontend/src/routes/callback/+server.ts`
- `frontend/src/routes/logout/+page.server.ts`
