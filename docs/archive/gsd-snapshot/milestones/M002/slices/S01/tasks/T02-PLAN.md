---
estimated_steps: 12
estimated_files: 3
skills_used: []
---

# T02: Rewire app-shell layout to use Dock and remove sidebar

Why: The app-shell currently renders Sidebar + BottomNav with main content offset by 60px left margin. Need to replace with Dock and let content fill full width.

Do:
1. Update `frontend/src/lib/components/app-shell/app-shell.svelte`:
   - Remove Sidebar and BottomNav imports
   - Import and render Dock component
   - Remove `sm:ml-60` from main element
   - Add bottom padding for dock height (pb-16 or pb-18)
   - Keep Toaster
2. Delete `frontend/src/lib/components/app-shell/sidebar.svelte`
3. Delete `frontend/src/lib/components/app-shell/bottom-nav.svelte`
4. Remove sidebar-specific CSS variables from `frontend/src/routes/layout.css` (sidebar, sidebar-foreground, sidebar-primary, etc.) — OR keep them if Dock reuses them. Decision: keep vars since dock uses similar color semantics, just rename mental model.

Done when: app-shell.svelte imports Dock, no references to Sidebar/BottomNav remain in the codebase, main content has no left offset.

## Inputs

- `frontend/src/lib/components/app-shell/app-shell.svelte`
- `frontend/src/lib/components/app-shell/dock.svelte`

## Expected Output

- `frontend/src/lib/components/app-shell/app-shell.svelte`

## Verification

grep -q 'Dock' frontend/src/lib/components/app-shell/app-shell.svelte && ! grep -rq 'Sidebar' frontend/src/lib/components/app-shell/ && ! grep -rq 'BottomNav' frontend/src/lib/components/app-shell/
