# S01: Dock Navigation & App Shell

**Goal:** Replace sidebar navigation with a centered bottom dock. Desktop shows all nav icons in a centered horizontal bar. Mobile shows 3 pinned items (Transactions, Subscriptions, Debts) plus a menu button that opens a dialog with remaining items. Remove the sidebar entirely.
**Demo:** Centered bottom dock renders on desktop (all icons) and mobile (3 pinned + overflow menu dialog); all navigation routes work in new layout

## Must-Haves

- Dock renders centered at bottom of viewport on all screen sizes
- Desktop (≥768px): all 6 nav icons + logout visible in dock
- Mobile (<768px): 3 pinned items (Transactions, Subscriptions, Debts) + menu icon visible; tapping menu opens dialog with remaining items
- Active route highlighted in dock
- All existing routes navigable via new dock
- Sidebar completely removed
- Main content area fills full width (no left offset)
- svelte-check passes
- vite build succeeds

## Proof Level

- This slice proves: contract — visual rendering verified via build success and type-check; runtime verification deferred to browser testing in S07

## Integration Closure

Upstream surfaces consumed: none (first UI slice). New wiring: app-shell layout replaced; all pages render within dock-based layout; .dark class toggle point established on html element for S02. What remains: S02 adds theme detection script, S04 adds card layouts within this shell.

## Verification

- Run the task and slice verification checks for this slice.

## Tasks

- [x] **T01: Create Dock component replacing sidebar and bottom-nav** `est:1h`
  Why: The sidebar and scrolling bottom-nav need to be replaced with a single Dock component that renders a centered horizontal bar at the bottom of the viewport. Desktop shows all nav items as icons; mobile shows 3 pinned + overflow menu button.
  - Files: `frontend/src/lib/components/app-shell/dock.svelte`, `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`
  - Verify: test -f frontend/src/lib/components/app-shell/dock.svelte

- [x] **T02: Rewire app-shell layout to use Dock and remove sidebar** `est:30m`
  Why: The app-shell currently renders Sidebar + BottomNav with main content offset by 60px left margin. Need to replace with Dock and let content fill full width.
  - Files: `frontend/src/lib/components/app-shell/app-shell.svelte`, `frontend/src/lib/components/app-shell/sidebar.svelte`, `frontend/src/lib/components/app-shell/bottom-nav.svelte`
  - Verify: grep -q Dock frontend/src/lib/components/app-shell/app-shell.svelte

- [x] **T03: Add theme detection script to app.html for S02 readiness** `est:10m`
  Why: S02 depends on the .dark class toggle point on <html>. The inline script in app.html must run before first paint to prevent flash. Adding it in S01 establishes the contract S02 consumes without adding visual changes yet.
  - Files: `frontend/src/app.html`
  - Verify: grep -q prefers-color-scheme frontend/src/app.html

- [x] **T04: Verify build and type-check pass with new layout** `est:20m`
  Why: Must confirm no type errors or build failures from the layout restructuring before marking slice complete.
  - Files: `frontend/src/lib/components/app-shell/app-shell.svelte`, `frontend/src/lib/components/app-shell/dock.svelte`, `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`
  - Verify: cd frontend && npx svelte-check --threshold error

## Files Likely Touched

- frontend/src/lib/components/app-shell/dock.svelte
- frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte
- frontend/src/lib/components/app-shell/app-shell.svelte
- frontend/src/lib/components/app-shell/sidebar.svelte
- frontend/src/lib/components/app-shell/bottom-nav.svelte
- frontend/src/app.html
