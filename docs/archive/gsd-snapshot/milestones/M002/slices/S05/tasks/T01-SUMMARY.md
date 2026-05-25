---
id: T01
parent: S05
milestone: M002
key_files:
  - frontend/package.json
  - frontend/src/lib/server/workos.ts
  - frontend/src/lib/server/workos-session.ts
  - frontend/src/app.d.ts
  - frontend/src/hooks.server.ts
key_decisions:
  - Used AES-256-GCM (node:crypto built-in) instead of iron-session — iron-session was not present in the project and adding a dependency just for cookie sealing is unnecessary when Node crypto covers it cleanly.
  - scryptSync with fixed salt for key derivation — acceptable because each encryption uses a random 12-byte IV; the fixed salt avoids per-request salt storage while still protecting against brute-force on the password.
  - Updated hooks.server.ts to use new workos-session getSession to unblock svelte-check type check; the old HMAC session.ts is left in place for later removal in the auth-replacement task.
duration: 
verification_result: passed
completed_at: 2026-05-17T18:04:38.546Z
blocker_discovered: false
---

# T01: Installed @workos-inc/node, created WorkOS client singleton and AES-256-GCM session utilities, updated App types to WorkOS user shape

**Installed @workos-inc/node, created WorkOS client singleton and AES-256-GCM session utilities, updated App types to WorkOS user shape**

## What Happened

Installed @workos-inc/node (288 packages) in the frontend directory. Created `frontend/src/lib/server/workos.ts` with a lazy-initialized WorkOS singleton reading WORKOS_API_KEY from env and a `getAuthorizationUrl(redirectUri)` helper wrapping `workos.userManagement.getAuthorizationUrl`. Created `frontend/src/lib/server/workos-session.ts` with AES-256-GCM authenticated encryption (node:crypto) using a scrypt-derived 32-byte key from WORKOS_COOKIE_PASSWORD. Exported `sealSession`, `unsealSession`, `getSession`, `setSession`, `clearSession` with the session shape `{ accessToken, refreshToken, user: { id, email, firstName, lastName } }`. Updated `frontend/src/app.d.ts` to replace `{ username: string }` with the WorkOS user shape in both `App.Locals.session` and `App.PageData.session`. Updated `frontend/src/hooks.server.ts` to import from the new `workos-session` module to eliminate the type conflict and unblock svelte-check — this is a minimal stub; the full auth flow (redirect, callback, token exchange) will be wired in subsequent tasks.

## Verification

Ran `npx svelte-kit sync && npx svelte-check --threshold error` — completed with 0 errors (11 warnings, none in new files). Verified `@workos-inc/node` imports correctly with `node -e "const {WorkOS}=require('@workos-inc/node'); console.log(typeof WorkOS)"` → `function`.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && npm install @workos-inc/node` | 0 | pass | 21000ms |
| 2 | `node -e "const {WorkOS}=require('@workos-inc/node'); console.log(typeof WorkOS)"` | 0 | pass | 200ms |
| 3 | `cd frontend && npx svelte-kit sync && npx svelte-check --threshold error` | 0 | pass — 0 errors | 14000ms |

## Deviations

Updated `hooks.server.ts` was not explicitly listed in T01 outputs but was required to satisfy the svelte-check must-have — the new App.Locals type made the old `{ username }` assignment a type error.

## Known Issues

None.

## Files Created/Modified

- `frontend/package.json`
- `frontend/src/lib/server/workos.ts`
- `frontend/src/lib/server/workos-session.ts`
- `frontend/src/app.d.ts`
- `frontend/src/hooks.server.ts`
