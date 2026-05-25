---
id: T02
parent: S01
milestone: M002
key_files:
  - frontend/src/lib/components/app-shell/app-shell.svelte
key_decisions:
  - Used pb-20 (80px) for dock bottom padding — slightly more than dock height estimate to ensure content never clips behind the dock on any screen size
  - Sidebar and BottomNav deleted outright; no tombstone or compatibility shim needed since Dock is a full replacement
duration: 
verification_result: passed
completed_at: 2026-05-16T20:11:50.617Z
blocker_discovered: false
---

# T02: Rewired app-shell to import Dock, removed Sidebar and BottomNav, main content now fills full width with bottom padding for dock height

**Rewired app-shell to import Dock, removed Sidebar and BottomNav, main content now fills full width with bottom padding for dock height**

## What Happened

Updated app-shell.svelte to replace Sidebar and BottomNav imports with Dock. Removed `sm:ml-60` left margin from the main element (content now fills full width). Set `pb-20` bottom padding to clear the dock. Deleted sidebar.svelte and bottom-nav.svelte from the app-shell directory. No other files referenced these components outside the app-shell directory.

## Verification

Ran the three verification commands from the task plan: (1) grep confirmed Dock is imported in app-shell.svelte, (2) grep confirmed no Sidebar references remain in the app-shell directory, (3) grep confirmed no BottomNav references remain. Also confirmed no stray imports of the deleted files elsewhere in frontend/src/.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `grep -q 'Dock' frontend/src/lib/components/app-shell/app-shell.svelte && echo PASS` | 0 | pass | 20ms |
| 2 | `! grep -rq 'Sidebar' frontend/src/lib/components/app-shell/ && echo PASS` | 0 | pass | 18ms |
| 3 | `! grep -rq 'BottomNav' frontend/src/lib/components/app-shell/ && echo PASS` | 0 | pass | 16ms |
| 4 | `grep -rq 'sidebar\.svelte\|bottom-nav\.svelte' frontend/src/ || echo 'No stray refs'` | 1 | pass — no stray references found | 22ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/lib/components/app-shell/app-shell.svelte`
