---
id: T04
parent: S01
milestone: M001
key_files:
  - frontend/src/lib/components/app-shell/sidebar.svelte
  - frontend/src/lib/components/app-shell/bottom-nav.svelte
  - frontend/src/lib/components/app-shell/app-shell.svelte
  - frontend/src/routes/+layout.svelte
  - frontend/src/routes/+page.svelte
  - frontend/src/routes/logout/+page.server.ts
  - frontend/src/hooks.server.ts
key_decisions:
  - Added /logout to PUBLIC_PATHS in hooks.server.ts — without it the auth guard would redirect unauthenticated requests to /login before the logout load function can clear the cookie, causing a redirect loop on second logout attempt
  - Logout is a load function (not an action) on +page.server.ts — SvelteKit server loads run on GET navigation, which is what an href link triggers; no form POST needed
  - Sidebar uses CSS hidden/flex via Tailwind sm: variants rather than JS-gated rendering so the correct element is always in the DOM for SSR hydration
duration: 
verification_result: passed
completed_at: 2026-05-13T20:34:39.140Z
blocker_discovered: false
---

# T04: Responsive authenticated app shell with sidebar (desktop) and bottom nav (mobile), dashboard placeholder, and logout route shipping clean with 0 type errors

**Responsive authenticated app shell with sidebar (desktop) and bottom nav (mobile), dashboard placeholder, and logout route shipping clean with 0 type errors**

## What Happened

Created three app-shell components under `frontend/src/lib/components/app-shell/`: `sidebar.svelte` (fixed 240px left panel, hidden below sm breakpoint, nav links with Lucide icons, active-route highlighting via `$page.url.pathname`, logout link at bottom), `bottom-nav.svelte` (fixed bottom bar, visible only below sm, same 4 nav items plus logout as icon+label tabs), and `app-shell.svelte` (responsive wrapper using `sm:ml-60` for sidebar offset and `pb-16 sm:pb-0` for bottom-nav clearance).

Updated `+layout.svelte` to conditionally wrap `children` in `AppShell` when `data.session` is truthy, rendering the bare layout otherwise (login page gets no chrome). Updated `+page.svelte` to a shadcn `Card` with 'Dashboard' title and placeholder copy. Created `logout/+page.server.ts` as a `load` function that calls `cookies.delete(COOKIE_NAME, { path: '/' })` then `redirect(303, '/login')`.

Also added `/logout` to `PUBLIC_PATHS` in `hooks.server.ts` to prevent the auth guard from intercepting the logout route and causing an infinite redirect loop when the cookie has already been cleared.

`bun install` was required first as `node_modules` was absent in the worktree; after install, `bun run check` completed with 0 errors and 1 pre-existing warning (login page state reference from T03).

## Verification

Ran `bun run check` (svelte-kit sync + svelte-check): 0 errors, 1 pre-existing warning. Confirmed `src/lib/components/app-shell/app-shell.svelte` and `src/routes/logout/+page.server.ts` exist. Verified `sm:` Tailwind breakpoints present in both `app-shell.svelte` (sidebar offset, bottom-nav padding) and `sidebar.svelte` (hidden below sm).

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run check` | 0 | pass — 0 errors, 1 pre-existing warning | 5805ms |
| 2 | `test -f frontend/src/lib/components/app-shell/app-shell.svelte` | 0 | pass | 5ms |
| 3 | `test -f frontend/src/routes/logout/+page.server.ts` | 0 | pass | 5ms |
| 4 | `grep -q 'sm:' frontend/src/lib/components/app-shell/app-shell.svelte` | 0 | pass | 5ms |
| 5 | `grep -q 'sm:' frontend/src/lib/components/app-shell/sidebar.svelte` | 0 | pass | 5ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`
- `frontend/src/lib/components/app-shell/app-shell.svelte`
- `frontend/src/routes/+layout.svelte`
- `frontend/src/routes/+page.svelte`
- `frontend/src/routes/logout/+page.server.ts`
- `frontend/src/hooks.server.ts`
