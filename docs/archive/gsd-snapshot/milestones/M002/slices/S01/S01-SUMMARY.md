---
id: S01
parent: M002
milestone: M002
provides:
  - New app-shell layout with dock component at bottom of viewport
  - Desktop dock: all 6 nav icons + logout in centered horizontal bar
  - Mobile dock: 3 pinned items (Transactions, Subscriptions, Debts) + overflow dialog
  - sidebar.svelte and bottom-nav.svelte removed — no compatibility shims
  - Main content fills full viewport width (no left offset)
  - .dark class toggle point on <html> via inline prefers-color-scheme script in app.html
requires:
  []
affects:
  - S02
  - S04
key_files:
  - frontend/src/lib/components/app-shell/dock.svelte
  - frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte
  - frontend/src/lib/components/app-shell/app-shell.svelte
  - frontend/src/app.html
key_decisions:
  - Reused sidebar-* CSS vars (--sidebar-bg, --sidebar-border, --sidebar-accent, --sidebar-fg) for dock theming — no new dock-* vars needed, keeps token count low
  - Desktop tooltips via CSS group-hover opacity transition — no JS tooltip library
  - Overflow dialog receives nav items as prop from dock, keeping the two components decoupled
  - Logout in dock-overflow-dialog uses inline SVG path rather than importing LogOut from @lucide/svelte — simpler than extending NavItem interface
  - pb-20 (80px) bottom padding on main content — slightly over dock height to ensure nothing clips at any screen size
  - Theme script placed before %sveltekit.head% to guarantee .dark class is set before any stylesheet injection
patterns_established:
  - sidebar-* CSS vars are the canonical theming surface for the app chrome — dock, future panels, and overlays should reuse these rather than adding new tokens
  - Inline script before %sveltekit.head% is the correct placement for FOUC-prevention scripts in SvelteKit
observability_surfaces:
  - N/A — pure frontend layout change; no runtime services, background jobs, or health endpoints introduced
drill_down_paths:
  - .gsd/milestones/M002/slices/S01/tasks/T01-SUMMARY.md
  - .gsd/milestones/M002/slices/S01/tasks/T02-SUMMARY.md
  - .gsd/milestones/M002/slices/S01/tasks/T03-SUMMARY.md
  - .gsd/milestones/M002/slices/S01/tasks/T04-SUMMARY.md
duration: ""
verification_result: passed
completed_at: 2026-05-16T20:15:50.883Z
blocker_discovered: false
---

# S01: Dock Navigation & App Shell

**Replaced sidebar + bottom-nav with a centered glass-morphism dock; desktop shows all nav icons, mobile shows 3 pinned items plus overflow dialog; theme-detection script wired in app.html for S02.**

## What Happened

S01 shipped the new app-shell architecture in four tasks. T01 created `dock.svelte` and `dock-overflow-dialog.svelte`: dock renders as a centered horizontal bar at the bottom of the viewport, reusing existing `sidebar-*` CSS variables for consistent theming, with CSS-only `group-hover` tooltips on desktop and a prop-driven overflow list on mobile. T02 rewired `app-shell.svelte` to import Dock and removed the Sidebar and BottomNav components entirely — both files were deleted, not archived, since Dock is a full replacement. Main content now fills full width with `pb-20` bottom padding so nothing clips behind the dock. T03 planted the inline `prefers-color-scheme` script in `app.html` before `%sveltekit.head%`, establishing the `.dark` class toggle point on `<html>` that S02 will consume; the script runs before first paint, preventing FOUC. T04 confirmed the build is clean: `npx vite build` exited 0 with 6861 modules transformed, and `npx svelte-check --threshold error` found zero errors in any S01 file (11 pre-existing errors in `native-date-picker.svelte` and `subscriptions/+page.svelte` remain, unchanged from main branch before this milestone).

## Verification

1. dock.svelte present: `test -f frontend/src/lib/components/app-shell/dock.svelte` — OK
2. dock-overflow-dialog.svelte present — OK
3. Dock imported in app-shell.svelte: `grep -q Dock` — OK
4. No Sidebar references in app-shell directory — OK
5. No BottomNav references in app-shell directory — OK
6. sidebar.svelte deleted — confirmed absent
7. bottom-nav.svelte deleted — confirmed absent
8. prefers-color-scheme in app.html: `grep -q prefers-color-scheme` — OK
9. `npx vite build` — exit 0, 6861 modules (T04-SUMMARY)
10. `npx svelte-check --threshold error` — 0 errors in S01 files (T04-SUMMARY)

## Requirements Advanced

- R002 — Dock component created and wired into app-shell: desktop shows all nav icons in centered bar, mobile shows 3 pinned items plus overflow menu dialog — full structural implementation of the dock navigation requirement

## Requirements Validated

- R002 — dock.svelte and dock-overflow-dialog.svelte confirmed present; Dock imported in app-shell.svelte; vite build exits 0; svelte-check reports 0 errors in S01 files — structural contract satisfied

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

Logout button in dock-overflow-dialog renders an inline SVG instead of importing the LogOut icon from @lucide/svelte. The NavItem interface covers the main nav items array passed as props; adding a separate import for a single icon in that component was simpler than extending the interface. Visually equivalent.

## Known Limitations

Runtime rendering (correct icon highlight on active route, overflow dialog open/close on real device, glass-morphism appearance) is not verified at contract level — deferred to browser testing in S07 per the slice's stated proof level. 11 pre-existing svelte-check errors (native-date-picker type mismatch, Select import missing in subscriptions page) remain; out of S01 scope.

## Follow-ups

S02 should read the inline theme script in app.html to understand where the .dark class is toggled before adding its own theme-store logic. S04 card layouts render within the full-width main content area established here — no layout changes needed in S04.

## Files Created/Modified

- `frontend/src/lib/components/app-shell/dock.svelte` — New: centered bottom dock with glass-morphism; desktop all-icons bar, mobile 3-pinned + overflow menu button
- `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte` — New: dialog listing remaining nav items for mobile overflow
- `frontend/src/lib/components/app-shell/app-shell.svelte` — Modified: imports Dock, removed Sidebar/BottomNav, main content fills full width with pb-20
- `frontend/src/app.html` — Modified: inline prefers-color-scheme script added before %sveltekit.head% to toggle .dark on <html>
- `frontend/src/lib/components/app-shell/sidebar.svelte` — Deleted: replaced by dock
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — Deleted: replaced by dock
