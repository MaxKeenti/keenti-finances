# S05: Passkey Auth via WorkOS

**Goal:** Password auth replaced entirely with passkey-only login via WorkOS AuthKit; passkey registration and authentication works end-to-end using @workos-inc/node SDK with manual OAuth/PKCE flow in SvelteKit hooks and routes; backend auth code removed
**Demo:** Password auth replaced entirely; passkey registration and login works end-to-end via WorkOS AuthKit

## Must-Haves

- 1. `bun install` succeeds with @workos-inc/node added\n2. Unauthenticated request to protected route redirects to WorkOS hosted auth UI\n3. After passkey auth, callback exchanges code for session, sets encrypted session cookie, redirects to /\n4. Protected routes read WorkOS session from cookie and populate locals.session\n5. Logout clears WorkOS session and redirects to login\n6. layout.svelte conditional rendering still works with new session shape\n7. Backend auth endpoint, AuthService, BcryptPasswordHasher removed; Flyway migration makes password_hash nullable and adds workos_user_id\n8. `bun run check` passes with no new errors\n9. `bun run build` exits 0

## Proof Level

- This slice proves: integration — real WorkOS SDK wired into SvelteKit hooks/routes; full auth flow requires runtime WorkOS credentials but structural correctness verified via build + type check

## Integration Closure

Upstream surfaces consumed: app.d.ts session type (used by layout.svelte, layout.server.ts), hooks.server.ts handle export, login/logout routes, backend AuthResource/AuthService.\nNew wiring: WorkOS Node SDK in hooks.server.ts handle chain; /auth/callback route for OAuth code exchange; encrypted session cookie replacing HMAC cookie; /login route redirects to WorkOS instead of rendering form.\nWhat remains before milestone is truly usable end-to-end: S06 (deferred fixes + backend tests), S07 (Railway deployment with WorkOS env vars configured)

## Verification

- Console logs on auth events: [workos-auth] redirect, callback, session-create, session-refresh, logout. Session cookie inspection via browser devtools (encrypted, not readable). WorkOS dashboard shows authentication events and passkey registrations.

## Tasks

- [x] **T01: Install WorkOS Node SDK and build session encryption utilities** `est:45m`
  Why: D020 confirmed @workos-inc/authkit-sveltekit does not exist on npm. We must use @workos-inc/node directly and implement session cookie encryption ourselves. This task sets up the SDK dependency and the session infrastructure that all subsequent tasks depend on.
  - Files: `frontend/package.json`, `frontend/src/lib/server/workos.ts`, `frontend/src/lib/server/workos-session.ts`
  - Verify: grep -q workos-inc/node frontend/package.json

- [x] **T02: Replace hooks.server.ts and auth routes with WorkOS OAuth/PKCE flow** `est:1h30m`
  Why: This is the core auth replacement — the highest-risk piece identified in research. hooks.server.ts must validate the WorkOS encrypted session cookie instead of the HMAC cookie, and the login/logout/callback routes must implement the OAuth authorization code flow with PKCE.
  - Files: `frontend/src/hooks.server.ts`, `frontend/src/routes/auth/login/+server.ts`, `frontend/src/routes/auth/callback/+server.ts`, `frontend/src/routes/logout/+page.server.ts`, `frontend/src/routes/login/+page.server.ts`, `frontend/src/routes/login/+page.svelte`
  - Verify: grep -q workos-session frontend/src/hooks.server.ts

- [x] **T03: Update session types, layout, and all session consumers** `est:30m`
  Why: The session shape changes from `{ username: string }` to a WorkOS user object. Every file reading `locals.session` or `data.session` must be updated, and app.d.ts must declare the new types. The layout.svelte conditional rendering must work with the new shape.
  - Files: `frontend/src/app.d.ts`, `frontend/src/routes/+layout.server.ts`, `frontend/src/routes/+layout.svelte`, `frontend/src/lib/server/session.ts`
  - Verify: cd frontend && bun run build

- [x] **T04: Remove backend auth code and add Flyway migration for user table** `est:45m`
  Why: With WorkOS handling authentication, the backend password auth infrastructure is dead code. The user table needs password_hash made nullable (existing row has a value) and a new workos_user_id column for future mapping. Backend auth classes must be removed cleanly.
  - Files: `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java`, `backend/src/main/java/com/keenti/finances/application/service/AuthService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/model/User.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`, `backend/src/main/resources/db/migration/V9__workos_auth_migration.sql`
  - Verify: cd backend && ./mvnw compile -q

## Files Likely Touched

- frontend/package.json
- frontend/src/lib/server/workos.ts
- frontend/src/lib/server/workos-session.ts
- frontend/src/hooks.server.ts
- frontend/src/routes/auth/login/+server.ts
- frontend/src/routes/auth/callback/+server.ts
- frontend/src/routes/logout/+page.server.ts
- frontend/src/routes/login/+page.server.ts
- frontend/src/routes/login/+page.svelte
- frontend/src/app.d.ts
- frontend/src/routes/+layout.server.ts
- frontend/src/routes/+layout.svelte
- frontend/src/lib/server/session.ts
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java
- backend/src/main/java/com/keenti/finances/application/service/AuthService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java
- backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java
- backend/src/main/java/com/keenti/finances/domain/model/User.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java
- backend/src/main/resources/db/migration/V9__workos_auth_migration.sql
