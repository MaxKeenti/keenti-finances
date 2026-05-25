---
estimated_steps: 5
estimated_files: 4
skills_used:
  - svelte-code-writer
---

# T01: Install WorkOS Node SDK and build auth session utilities

**Slice:** S05 — Passkey Auth via WorkOS
**Milestone:** M002

## Description

The @workos-inc/authkit-sveltekit package does not exist (D020). Install @workos-inc/node for the WorkOS API and create a session utility module to encrypt/decrypt session cookies (replacing the current HMAC module). Also create a singleton WorkOS client initialized from env vars and update app.d.ts session types to the new WorkOS user shape.

## Steps

1. Run `npm install @workos-inc/node` in the frontend directory
2. Create `frontend/src/lib/server/workos.ts` — singleton WorkOS client initialized from env vars (WORKOS_API_KEY, WORKOS_CLIENT_ID), plus helper to generate authorization URL
3. Create `frontend/src/lib/server/workos-session.ts` — session cookie utilities using iron-session-style encryption with WORKOS_COOKIE_PASSWORD (32+ char secret). Functions: `sealSession(data)`, `unsealSession(cookieValue)`, `getSession(cookies)`, `setSession(cookies, data)`, `clearSession(cookies)`. Session shape: `{ accessToken, refreshToken, user: { id, email, firstName, lastName } }`
4. Update `frontend/src/app.d.ts` — change `App.Locals.session` and `App.PageData.session` types from `{ username: string }` to the new WorkOS user shape `{ user: { id: string, email: string, firstName: string, lastName: string } } | null`
5. Verify: `npx svelte-check --threshold error` passes

## Must-Haves

- [ ] @workos-inc/node installed in frontend/package.json
- [ ] WorkOS client singleton module exists at frontend/src/lib/server/workos.ts
- [ ] Session seal/unseal utilities exist at frontend/src/lib/server/workos-session.ts
- [ ] app.d.ts updated with WorkOS session types
- [ ] svelte-check passes

## Verification

- `cd frontend && npx svelte-check --threshold error` exits 0
- `node -e "require('@workos-inc/node')"` or import resolves without error

## Verify Rules

- Use a real executable check, not prose.
- If the check needs file-content assertions, write a `node:test` file and run it with `node --test` or a package test script.
- Do not use inline `node -e` assertions for verification.

## Inputs

- `frontend/src/lib/server/session.ts` — existing HMAC session module being replaced
- `frontend/src/app.d.ts` — existing session type definitions
- `frontend/package.json` — dependency manifest

## Expected Output

- `frontend/package.json` — updated with @workos-inc/node dependency
- `frontend/src/lib/server/workos.ts` — new WorkOS client singleton
- `frontend/src/lib/server/workos-session.ts` — new session cookie utilities
- `frontend/src/app.d.ts` — updated session types for WorkOS user shape
