---
id: T03
parent: S01
milestone: M001
key_files:
  - frontend/src/lib/server/session.ts
  - frontend/src/routes/api/[...path]/+server.ts
  - frontend/src/app.d.ts
  - frontend/src/hooks.server.ts
  - frontend/src/routes/login/+page.server.ts
  - frontend/src/routes/login/+page.svelte
  - frontend/src/routes/+layout.server.ts
  - frontend/package.json
  - frontend/tsconfig.json
key_decisions:
  - Removed NodeNext module/moduleResolution from tsconfig.json — SvelteKit requires bundler resolution; NodeNext breaks $lib path aliases and virtual ./$types modules
  - Used timingSafeEqual for cookie validation to prevent timing-based forgery attacks
  - SESSION_SECRET has a dev fallback string so local development works without env configuration
  - superForm initialized with let { data } = $props() pattern — the Svelte warning about initial value capture is expected behavior for form initialization
  - Login page uses the sf (SuperForm object) reference for Form.Field, not the destructured form store
duration: 
verification_result: passed
completed_at: 2026-05-13T15:58:09.579Z
blocker_discovered: false
---

# T03: SvelteKit proxy, HMAC-signed session cookies, auth guard hook, and login page with superforms wired to Quarkus /api/auth/login

**SvelteKit proxy, HMAC-signed session cookies, auth guard hook, and login page with superforms wired to Quarkus /api/auth/login**

## What Happened

Installed zod (v4.4.3) and @types/node as direct dependencies. Removed conflicting NodeNext module/moduleResolution overrides from tsconfig.json (SvelteKit requires bundler resolution; the overrides caused $lib alias failures and virtual ./$types module errors). Also removed `rewriteRelativeImportExtensions` which was only needed with NodeNext.

Created all 7 output files:

1. `frontend/src/lib/server/session.ts` — HMAC-SHA256 session cookie utilities using Node's `node:crypto`. `createSessionCookieValue(username)` signs the value with the SESSION_SECRET env var (dev fallback provided). `validateSessionCookieValue(value)` uses `timingSafeEqual` to prevent timing attacks.

2. `frontend/src/routes/api/[...path]/+server.ts` — Catch-all proxy forwarding GET/POST/PUT/PATCH/DELETE to `http://localhost:8080/api/{path}`. Returns 502 on backend connection failure, 504 on 30-second timeout. Strips hop-by-hop headers.

3. `frontend/src/app.d.ts` — Added `session: { username: string } | null` to App.Locals and App.PageData.

4. `frontend/src/hooks.server.ts` — Auth guard handle hook that validates the session cookie, populates `event.locals.session`, and redirects unauthenticated requests to /login. Exempts /login, /api/auth/login, /_app/, and /static/ paths.

5. `frontend/src/routes/login/+page.server.ts` — Zod schema (username + password, min 1), superValidate on load and action. Action POSTs JSON to Quarkus auth endpoint, sets HTTP-only SameSite=Lax session cookie on 200, returns fail with message on 401/502. Logs login success/failure with username and timestamp.

6. `frontend/src/routes/login/+page.svelte` — Centered Card layout using shadcn components. superForm with zod4Client validators, formsnap Field/Control/FieldErrors for inline validation, server error shown as alert div, submitting state disables the button.

7. `frontend/src/routes/+layout.server.ts` — Passes `locals.session` as page data so layouts and pages can access session info.

## Verification

Ran `cd frontend && bun run check` — exits 0, 0 errors (1 Svelte warning about superForm initial value capture, expected and benign). Verified all file existence and content checks: hooks.server.ts exists, api/[...path]/+server.ts exists, login/+page.svelte exists, SESSION_SECRET present in session.ts. Backend compile (`./mvnw compile -q`) exits 0 confirming T01/T02 backend code is intact. Backend BCrypt and @Path annotations confirmed present.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run check && echo 'TYPE CHECK OK'` | 0 | pass | 6500ms |
| 2 | `test -f frontend/src/hooks.server.ts` | 0 | pass | 5ms |
| 3 | `test -f 'frontend/src/routes/api/[...path]/+server.ts'` | 0 | pass | 5ms |
| 4 | `test -f frontend/src/routes/login/+page.svelte` | 0 | pass | 5ms |
| 5 | `grep -q 'SESSION_SECRET' frontend/src/lib/server/session.ts` | 0 | pass | 10ms |
| 6 | `grep -q 'BCrypt.checkpw' backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java` | 0 | pass | 10ms |
| 7 | `grep -q '@Path' backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` | 0 | pass | 10ms |
| 8 | `cd backend && ./mvnw compile -q` | 0 | pass | 8200ms |

## Deviations

Removed `rewriteRelativeImportExtensions: true` and `module/moduleResolution: NodeNext` from tsconfig.json — these were pre-existing settings that conflicted with SvelteKit's required bundler module resolution and caused all $lib and ./$types imports to fail type checking. This is a correction to the project configuration, not a feature change.

## Known Issues

none

## Files Created/Modified

- `frontend/src/lib/server/session.ts`
- `frontend/src/routes/api/[...path]/+server.ts`
- `frontend/src/app.d.ts`
- `frontend/src/hooks.server.ts`
- `frontend/src/routes/login/+page.server.ts`
- `frontend/src/routes/login/+page.svelte`
- `frontend/src/routes/+layout.server.ts`
- `frontend/package.json`
- `frontend/tsconfig.json`
