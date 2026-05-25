# S01: Dock Navigation & App Shell — UAT

**Milestone:** M002
**Written:** 2026-05-16T20:15:50.884Z

# S01: Dock Navigation & App Shell — UAT

**Milestone:** M002
**Written:** 2026-05-16

## UAT Type

- UAT mode: artifact-driven
- Why this mode is sufficient: S01's proof level is "contract" — visual rendering is deferred to browser testing in S07. The slice goal is structural: correct files exist, imports are wired, build is clean, and the .dark toggle point is in place. All of these are verifiable from artifacts and build output without a running browser.

## Preconditions

- Working directory: `frontend/`
- `bun install` has been run (node_modules present)
- On branch `milestone/M002`

## Smoke Test

Run `grep -q "Dock" frontend/src/lib/components/app-shell/app-shell.svelte && echo OK` — should print `OK`, confirming the new dock is wired into the app shell.

## Test Cases

### 1. Dock component files exist

1. Run `test -f frontend/src/lib/components/app-shell/dock.svelte && echo OK`
2. Run `test -f frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte && echo OK`
3. **Expected:** Both commands print `OK`

### 2. Sidebar and BottomNav are fully removed

1. Run `test ! -f frontend/src/lib/components/app-shell/sidebar.svelte && echo "sidebar gone"`
2. Run `test ! -f frontend/src/lib/components/app-shell/bottom-nav.svelte && echo "bottom-nav gone"`
3. Run `grep -rq "Sidebar\|BottomNav" frontend/src/lib/components/app-shell/ && echo "FOUND (bad)" || echo "clean (good)"`
4. **Expected:** Both files absent; no Sidebar or BottomNav references remain

### 3. Theme detection script is in app.html

1. Run `grep "prefers-color-scheme" frontend/src/app.html`
2. **Expected:** Script line appears before `%sveltekit.head%`; confirms .dark toggle point established for S02

### 4. Build passes clean

1. `cd frontend && npx vite build`
2. **Expected:** Exit 0; no errors related to dock, app-shell, or app.html

### 5. Type-check passes for S01 files

1. `cd frontend && npx svelte-check --threshold error 2>&1 | grep -E "dock|app-shell|app\.html"` 
2. **Expected:** No output (zero errors in S01 files); pre-existing errors in native-date-picker.svelte and subscriptions/+page.svelte are acceptable

## Edge Cases

### Overflow dialog decoupling

1. Open `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`
2. Confirm the component receives nav items as a prop (not hardcoded)
3. **Expected:** Items prop drives the list; no static nav item array inside the dialog

### No stray Sidebar imports anywhere in frontend/src/

1. Run `grep -r "from.*sidebar" frontend/src/ --include="*.svelte" --include="*.ts"`
2. **Expected:** No matches (sidebar fully removed)

## Failure Signals

- `dock.svelte` or `dock-overflow-dialog.svelte` not found — T01 work missing
- `Sidebar` or `BottomNav` still referenced in app-shell directory — T02 incomplete
- `prefers-color-scheme` absent from app.html — T03 incomplete
- `npx vite build` exits non-zero with errors in dock/app-shell files — T04 regression
- New svelte-check errors in S01 files — type contract broken

## Not Proven By This UAT

- Visual rendering: that the dock actually appears centered at the bottom of the browser viewport
- Desktop layout: all 6 icons visible and spaced correctly in a horizontal bar
- Mobile layout: 3 pinned items + menu icon visible at <768px; overflow dialog opens and closes on tap
- Active route highlighting: correct icon is highlighted when navigating
- Glass-morphism effect: backdrop-filter renders correctly across browsers
- .dark class actually toggles: that the prefers-color-scheme script fires and sets the class before paint in a real browser session

These gaps are deferred to S07 browser/production verification.

## Notes for Tester

- 11 pre-existing svelte-check errors will appear (native-date-picker type mismatch, subscriptions Select import) — these are not regressions from S01 and can be ignored
- `bun install` must run first if node_modules are absent in the worktree
- S02 will add the theme-store logic that reads the .dark class; verify S01's app.html script in context of S02's UAT for the full FOUC-prevention story

