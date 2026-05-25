---
estimated_steps: 7
estimated_files: 6
skills_used:
  - svelte-code-writer
---

# T03: Replace login and logout routes and update layout session consumption

**Slice:** S05 — Passkey Auth via WorkOS
**Milestone:** M002

## Description

Login page currently renders a username/password form and POSTs to the backend. It must become a redirect to WorkOS. Logout must clear the WorkOS session. Layout and any components consuming `data.session.username` must use the new WorkOS user shape. Old HMAC session module deleted.

## Steps

1. Rewrite `frontend/src/routes/login/+page.server.ts`: if session exists redirect to `/`, otherwise redirect to WorkOS authorization URL. Remove form actions entirely.
2. Rewrite `frontend/src/routes/login/+page.svelte`: simple "Redirecting to login..." message or "Sign in with Passkey" button
3. Rewrite `frontend/src/routes/logout/+page.server.ts`: clear session cookie via `clearSession(cookies)`, redirect to `/login`
4. Update `frontend/src/routes/+layout.server.ts`: return the new session shape (user object instead of username)
5. Update `frontend/src/routes/+layout.svelte`: change `data.session` usage from `{ username }` to `{ user: { email, firstName } }` shape. Grep for all `session.username` references and update.
6. Delete `frontend/src/lib/server/session.ts` (old HMAC module)
7. Verify: `npx svelte-check --threshold error` and `npx vite build` pass

## Must-Haves

- [ ] Login page redirects to WorkOS instead of showing a form
- [ ] Logout clears WorkOS session cookie
- [ ] Layout uses new session type (user object)
- [ ] All session.username references updated to new shape
- [ ] Old session.ts deleted
- [ ] vite build passes

## Verification

- `cd frontend && npx vite build` exits 0
- `cd frontend && npx svelte-check --threshold error` exits 0

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/lib/server/workos.ts` — WorkOS client from T01
- `frontend/src/lib/server/workos-session.ts` — session utilities from T01
- `frontend/src/hooks.server.ts` — rewritten hooks from T02
- `frontend/src/routes/login/+page.server.ts` — existing login form handler
- `frontend/src/routes/login/+page.svelte` — existing login form
- `frontend/src/routes/logout/+page.server.ts` — existing logout handler
- `frontend/src/routes/+layout.server.ts` — existing layout server load
- `frontend/src/routes/+layout.svelte` — existing layout consuming session

## Expected Output

- `frontend/src/routes/login/+page.server.ts` — rewritten to redirect to WorkOS
- `frontend/src/routes/login/+page.svelte` — simplified redirect/button page
- `frontend/src/routes/logout/+page.server.ts` — rewritten to clear WorkOS session
- `frontend/src/routes/+layout.server.ts` — updated session shape
- `frontend/src/routes/+layout.svelte` — updated session consumption
- `frontend/src/lib/server/session.ts` — deleted
