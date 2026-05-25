---
estimated_steps: 5
estimated_files: 2
skills_used:
  - svelte-code-writer
---

# T02: Replace hooks.server.ts with WorkOS session validation and create callback route

**Slice:** S05 — Passkey Auth via WorkOS
**Milestone:** M002

## Description

The core auth gate — hooks.server.ts must validate WorkOS sessions instead of HMAC cookies, and we need a callback route for the OAuth code exchange after WorkOS redirects back. On each request: unseal session cookie, if valid set event.locals.session with user data, if expired attempt refresh via WorkOS SDK, if no session and not public path redirect to WorkOS authorization URL.

## Steps

1. Rewrite `frontend/src/hooks.server.ts`: import WorkOS client and session utilities from T01; on each request unseal session cookie → validate → refresh if expired → redirect to WorkOS if unauthenticated
2. Update PUBLIC_PATHS to include `/callback`, remove `/api/auth/login`
3. Generate WorkOS authorization URL via `workos.userManagement.getAuthorizationURL({ provider: 'authkit', redirectUri, clientId })`
4. Create `frontend/src/routes/callback/+server.ts`: GET handler that extracts `code` query param, exchanges via `workos.userManagement.authenticateWithCode()`, seals session, redirects to `/`
5. Verify: `npx svelte-check --threshold error` passes

## Failure Modes

| Dependency | On error | On timeout | On malformed response |
|------------|----------|-----------|----------------------|
| WorkOS API (code exchange) | Log error, redirect to /login with error param | 10s timeout, redirect to /login | Log malformed response, redirect to /login |
| WorkOS API (token refresh) | Clear session, redirect to WorkOS login | Clear session, redirect to WorkOS login | Clear session, redirect to WorkOS login |

## Must-Haves

- [ ] hooks.server.ts uses WorkOS session validation instead of HMAC
- [ ] Unauthenticated requests redirect to WorkOS authorization URL
- [ ] /callback route handles OAuth code exchange and creates session
- [ ] PUBLIC_PATHS updated correctly
- [ ] svelte-check passes

## Verification

- `cd frontend && npx svelte-check --threshold error` exits 0

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/lib/server/workos.ts` — WorkOS client singleton from T01
- `frontend/src/lib/server/workos-session.ts` — session utilities from T01
- `frontend/src/hooks.server.ts` — existing HMAC-based auth hooks
- `frontend/src/app.d.ts` — updated session types from T01

## Expected Output

- `frontend/src/hooks.server.ts` — rewritten with WorkOS session validation
- `frontend/src/routes/callback/+server.ts` — new OAuth callback handler
