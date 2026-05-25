# S05 Research: Passkey Auth via WorkOS

**Depth:** Deep research — replacing the entire auth layer with an external service (WorkOS AuthKit), unfamiliar library integration, session management architecture change, domain-bound passkey constraints.

## Summary

S05 replaces the current HMAC-cookie password auth with WorkOS AuthKit passkey-only login. The current auth is straightforward: SvelteKit hooks validate an HMAC-signed cookie containing the username, and the backend has a `/api/auth/login` endpoint that checks bcrypt passwords. WorkOS AuthKit for SvelteKit (`@workos/authkit-sveltekit`) handles the WebAuthn ceremony, session management, and provides middleware that replaces the custom hooks logic. The backend auth endpoint and password infrastructure become dead code.

## Recommendation

1. Install `@workos-inc/authkit-sveltekit` and configure environment variables
2. Replace hooks.server.ts session validation with WorkOS middleware
3. Replace login page with WorkOS redirect flow
4. Update app.d.ts session types
5. Remove backend auth endpoint and password infrastructure (or leave as dead code for S06 cleanup)

## Implementation Landscape

### Current Auth Architecture

**Session management** (`frontend/src/lib/server/session.ts`):
- HMAC-signed cookie: `username.signature` format
- Cookie name: `session`, HttpOnly, sameSite=lax, 7-day maxAge
- Uses `SESSION_SECRET` env var for HMAC key
- Functions: `createSessionCookieValue()`, `validateSessionCookieValue()`

**SvelteKit hooks** (`frontend/src/hooks.server.ts`):
- Validates HMAC cookie on every request
- PUBLIC_PATHS: `/login`, `/api/auth/login`, `/logout`, `/public`, `/health`
- Sets `event.locals.session = { username }` or redirects to `/login`

**Login flow** (`frontend/src/routes/login/+page.server.ts`):
- sveltekit-superforms with Zod schema (username + password)
- Calls `POST ${BACKEND_URL}/api/auth/login`
- On success: sets session cookie, redirects to `/`

**Backend auth** (`backend/.../AuthResource.java`):
- `POST /api/auth/login` — validates credentials via AuthService
- AuthService uses BcryptPasswordHasher to verify
- User model: `id`, `username`, `passwordHash`

**App types** (`frontend/src/app.d.ts`):
- `App.Locals.session: { username: string } | null`

### WorkOS AuthKit Integration

**Package:** `@workos-inc/authkit-sveltekit` (note: npm org is `@workos-inc`, not `@workos`)

**Required env vars:**
- `WORKOS_API_KEY` — from WorkOS dashboard
- `WORKOS_CLIENT_ID` — from WorkOS dashboard  
- `WORKOS_COOKIE_PASSWORD` — 32+ char random string for session encryption
- `WORKOS_REDIRECT_URI` — callback URL (e.g. `https://app.keenti.com/callback`)

**Integration pattern:**
1. WorkOS provides a `handleAuth()` function for hooks.server.ts
2. Auth flow: redirect to WorkOS hosted UI → user authenticates with passkey → redirect back with code → exchange for session
3. Session is managed by WorkOS SDK (encrypted cookie, not HMAC)
4. `withAuth()` helper gets session in server load functions

**Key changes to hooks.server.ts:**
```typescript
import { handleAuth } from '@workos-inc/authkit-sveltekit';
export const handle = handleAuth();
```

**Key changes to routes:**
- Login page becomes a redirect to WorkOS (`signIn()` helper)
- Callback route handles the OAuth code exchange
- Logout calls WorkOS signOut

**Session shape changes from:**
```typescript
{ username: string }
```
**To WorkOS session:**
```typescript
{ user: { id, email, firstName, lastName, ... }, accessToken, ... }
```

### Files to Create/Modify

**Frontend — Replace:**
| File | Action |
|------|--------|
| `frontend/src/hooks.server.ts` | Replace HMAC validation with `handleAuth()` |
| `frontend/src/lib/server/session.ts` | Delete or gut — WorkOS manages sessions |
| `frontend/src/routes/login/+page.server.ts` | Replace with WorkOS signIn redirect |
| `frontend/src/routes/login/+page.svelte` | Replace form with redirect or "Sign in with passkey" button |
| `frontend/src/routes/logout/+page.server.ts` | Replace with WorkOS signOut |
| `frontend/src/app.d.ts` | Update session type to WorkOS user shape |
| `frontend/src/routes/+layout.server.ts` | Update to use WorkOS session |

**Frontend — Create:**
| File | Purpose |
|------|---------|
| `frontend/src/routes/callback/+server.ts` | WorkOS OAuth callback handler (if not handled by handleAuth) |

**Backend — Remove/Deprecate:**
| File | Action |
|------|--------|
| `backend/.../rest/AuthResource.java` | Remove or mark deprecated |
| `backend/.../service/AuthService.java` | Remove |
| `backend/.../security/BcryptPasswordHasher.java` | Remove |
| `backend/.../port/in/AuthUseCase.java` | Remove |

**Backend — Keep but modify:**
| File | Action |
|------|--------|
| `backend/.../model/User.java` | passwordHash becomes optional/removed |
| `backend/.../persistence/UserEntity.java` | Column nullable or removed |

### Domain Constraint: Passkeys are Domain-Bound

Passkeys registered on one domain (e.g. localhost:5173) won't work on another (e.g. app.keenti.com). This means:
- Development uses a separate WorkOS environment/passkey set
- Production domain must be finalized before first passkey registration
- If domain changes after registration, all passkeys break

### Backend Auth Removal Considerations

The backend currently validates passwords only. With WorkOS:
- Frontend validates the session (WorkOS cookie) — no backend involvement in auth
- Backend API calls from SvelteKit server-side don't need auth headers (they're internal service-to-service calls on the same network)
- If backend ever needs to validate the caller, pass the WorkOS access token as Bearer header

**Current proxy** (`frontend/src/routes/api/[...path]/+server.ts`):
- Forwards all `/api/*` requests to backend
- Currently passes through headers but doesn't inject auth
- No change needed here unless backend needs token validation

## Natural Seams (Task Decomposition)

1. **Install + configure WorkOS** — `npm install @workos-inc/authkit-sveltekit`, env vars, WorkOS dashboard setup
2. **Replace hooks.server.ts** — Swap HMAC validation for `handleAuth()`
3. **Replace login/logout routes** — WorkOS signIn/signOut flow
4. **Update session types + layout** — app.d.ts, layout.server.ts, any components reading session
5. **Remove backend auth code** — AuthResource, AuthService, BcryptPasswordHasher (or defer to S06)
6. **Migration** — Make password_hash nullable or add workos_user_id column

## First Proof (Highest Risk / Biggest Unblocker)

The WorkOS `handleAuth()` integration in hooks.server.ts. If this doesn't work correctly with the existing route structure, nothing else matters. Prove: unauthenticated request → redirect to WorkOS → callback → session established → protected route accessible.

**Key risk:** `@workos-inc/authkit-sveltekit` compatibility with SvelteKit 2.x and Svelte 5. Check package compatibility before implementation.

## Verification

- `npm install` succeeds with `@workos-inc/authkit-sveltekit`
- `npx vite build` — exit 0
- `npx svelte-check --threshold error` — no new errors
- Manual test: access protected route → redirected to WorkOS → authenticate → redirected back with session
- Manual test: logout → session cleared → redirected to login
- Backend still compiles after auth code removal

## Constraints

- Passkey-only — no password fallback per architectural decision
- WorkOS free tier (1M MAU) — sufficient for single-user app
- Domain-bound passkeys — must use consistent domain across environments
- `SESSION_SECRET` env var becomes obsolete — replaced by `WORKOS_COOKIE_PASSWORD`
- PUBLIC_PATHS logic may be handled differently by WorkOS middleware (check docs)
- Backend auth endpoint removal should not break any other functionality (verify no other callers)

## Skill Discovery

**WorkOS AuthKit for SvelteKit** — no installed skill found in available_skills. Consider:
```bash
npx skills find "workos"
npx skills find "sveltekit auth"
```

The `svelte-code-writer` and `svelte-core-bestpractices` skills are already installed and relevant for the SvelteKit integration work.

## Don't Hand-Roll

- Session encryption — use WorkOS SDK's built-in session management
- WebAuthn ceremony — WorkOS handles the full passkey registration/authentication flow
- CSRF protection — WorkOS SDK handles state parameters in the OAuth flow
- Token refresh — WorkOS SDK handles session refresh transparently
