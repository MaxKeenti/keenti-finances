---
id: T03
parent: S05
milestone: M002
key_files:
  - frontend/src/routes/login/+page.server.ts
  - frontend/src/routes/login/+page.svelte
  - frontend/src/routes/logout/+page.server.ts
  - frontend/src/lib/server/workos.ts
  - frontend/src/routes/callback/+server.ts
  - frontend/src/hooks.server.ts
key_decisions:
  - WorkOS client converted from top-level singleton to lazy singleton (getWorkOS()) — build-time module evaluation doesn't have env vars, so eager init throws during vite build.
  - Logout redirects to /login (not /) to avoid an immediate re-auth redirect loop for unauthenticated users.
duration: 
verification_result: passed
completed_at: 2026-05-17T22:54:10.482Z
blocker_discovered: false
---

# T03: Replaced password login with WorkOS redirect, updated logout to redirect to /login, deleted old session.ts, and made WorkOS client lazy to fix vite build

**Replaced password login with WorkOS redirect, updated logout to redirect to /login, deleted old session.ts, and made WorkOS client lazy to fix vite build**

## What Happened

Login page server load now redirects unauthenticated users to WorkOS authorization URL (via getAuthorizationUrl) and authenticated users to /. The login +page.svelte was reduced to a minimal "Redirecting..." placeholder since the server always redirects before the page renders. Logout was corrected to redirect to /login (T02 had it redirecting to / which would cause an immediate re-auth loop). The old HMAC session.ts was deleted. Layout and app-shell had no session.username references — the layout only checks data.session truthiness, so no changes were needed there. The vite build initially failed because workos.ts initialized the WorkOS client as a top-level singleton, causing the module to throw at build-time (no WORKOS_API_KEY in the build environment). Fixed by converting to a lazy singleton via getWorkOS(). Updated callback/+server.ts and hooks.server.ts to call getWorkOS() instead of importing the old `workos` export.

## Verification

svelte-check --threshold error: 0 errors. npx vite build: exits 0 (circular dep warnings are from third-party deps, not our code).

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && npx svelte-check --threshold error` | 0 | pass | 1200ms |
| 2 | `cd frontend && npx vite build` | 0 | pass | 45000ms |

## Deviations

workos.ts was refactored from an eagerly-initialized singleton export (`workos`) to a lazy getter (`getWorkOS()`) to fix a vite build failure — not in the original task plan but required for the build to succeed.

## Known Issues

None.

## Files Created/Modified

- `frontend/src/routes/login/+page.server.ts`
- `frontend/src/routes/login/+page.svelte`
- `frontend/src/routes/logout/+page.server.ts`
- `frontend/src/lib/server/workos.ts`
- `frontend/src/routes/callback/+server.ts`
- `frontend/src/hooks.server.ts`
