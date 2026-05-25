---
estimated_steps: 40
estimated_files: 8
skills_used: []
---

# T03: Wire SvelteKit proxy to Quarkus, implement session management and login page with auth guard

Set up SvelteKit as the auth and proxy layer. Create a catch-all +server.ts under /api/[...path] that forwards requests to the Quarkus backend (http://localhost:8080). Implement session management using signed HTTP-only cookies (SvelteKit cookies API with HMAC-SHA256 signing via Node crypto). Create the login page with a superforms-validated form that POSTs to a +page.server.ts action, which calls the Quarkus /api/auth/login, and on success sets the session cookie and redirects to /. Add hooks.server.ts handle hook that checks for valid session cookie on all routes except /login and /api/auth/login, redirecting to /login if missing/invalid. Update app.d.ts with session types in App.Locals. Install zod dependency.

---
estimated_steps: 8
estimated_files: 8
skills_used:
  - svelte-code-writer
  - svelte-core-bestpractices
---

## Steps

1. Install zod: cd frontend && bun add zod
2. Create frontend/src/routes/api/[...path]/+server.ts — catch-all proxy that forwards GET/POST/PUT/DELETE/PATCH to http://localhost:8080/api/{path} with headers and body, returns Quarkus response with status and headers
3. Create frontend/src/lib/server/session.ts — session utility: createSessionCookie(username) returns signed value (username + HMAC-SHA256 signature), validateSessionCookie(value) returns username or null. Uses SESSION_SECRET env var with dev fallback.
4. Update frontend/src/app.d.ts — add session: { username: string } | null to App.Locals, add PageData types
5. Create frontend/src/hooks.server.ts — handle hook: validate session cookie, populate event.locals.session, redirect unauthenticated requests to /login (skip /login route and static assets)
6. Create frontend/src/routes/login/+page.server.ts — Zod schema (username: string min 1, password: string min 1), superforms load and actions. Action calls fetch to http://localhost:8080/api/auth/login with credentials, on 200 sets session cookie via cookies.set() and redirects to /, on 401 returns form with error message
7. Create frontend/src/routes/login/+page.svelte — centered login form using shadcn Card, Input, Button, Label components with superforms binding. Shows validation errors inline and server error as alert.
8. Create frontend/src/routes/+layout.server.ts — return { session: locals.session } as page data

## Must-Haves

- [ ] Proxy forwards all /api/* requests to Quarkus backend transparently
- [ ] Session cookie is HTTP-only, Secure in prod, signed with HMAC-SHA256, SameSite=Lax
- [ ] Login form validates with Zod via superforms
- [ ] hooks.server.ts redirects unauthenticated requests to /login
- [ ] /login page is accessible without session
- [ ] Successful login sets cookie and redirects to /
- [ ] SESSION_SECRET read from env vars (dev fallback for local development)

## Threat Surface

- **Abuse**: Session cookie forgery — mitigated by HMAC signing with secret
- **Data exposure**: Session cookie contains username (not sensitive for single-user), no password
- **Input trust**: Login form inputs validated by Zod; proxy forwards trusted internal API calls only

## Failure Modes

| Dependency | On error | On timeout | On malformed response |
|------------|----------|-----------|----------------------|
| Quarkus backend | Proxy returns 502 with error message | Proxy returns 504 after 30s timeout | Proxy forwards raw response |
| Session cookie | Invalid/tampered cookie → redirect to /login | N/A | Treated as invalid → redirect to /login |

## Verification

- cd frontend && bun run check exits 0
- test -f frontend/src/hooks.server.ts
- test -f frontend/src/routes/api/[...path]/+server.ts
- test -f frontend/src/routes/login/+page.svelte
- grep -q 'SESSION_SECRET' frontend/src/lib/server/session.ts

## Inputs

- `frontend/package.json — existing SvelteKit config to add zod dependency`
- `frontend/src/app.d.ts — app type declarations to extend with session types`
- `frontend/svelte.config.js — SvelteKit config for understanding adapter and routing`
- `frontend/src/routes/+layout.svelte — existing layout to understand structure`
- `frontend/src/lib/components/ui/card/index.ts — shadcn card component available`
- `frontend/src/lib/components/ui/input/index.ts — shadcn input component available`
- `frontend/src/lib/components/ui/form/index.ts — shadcn form component available`
- `frontend/src/lib/components/ui/label/index.ts — shadcn label component available`

## Expected Output

- `frontend/src/routes/api/[...path]/+server.ts — catch-all API proxy to Quarkus`
- `frontend/src/lib/server/session.ts — HMAC session cookie utilities`
- `frontend/src/app.d.ts — updated with session types in App.Locals`
- `frontend/src/hooks.server.ts — auth guard handle hook`
- `frontend/src/routes/login/+page.server.ts — login form action with Quarkus call`
- `frontend/src/routes/login/+page.svelte — login page UI with superforms`
- `frontend/src/routes/+layout.server.ts — session data passthrough to layout`
- `frontend/package.json — updated with zod dependency`

## Verification

cd frontend && bun run check && echo 'TYPE CHECK OK' && test -f src/hooks.server.ts && test -f src/routes/api/\[...path\]/+server.ts && test -f src/routes/login/+page.svelte && grep -q 'SESSION_SECRET' src/lib/server/session.ts && echo 'ALL CHECKS PASSED'
