---
estimated_steps: 6
estimated_files: 3
skills_used: []
---

# T04: Verify build and type-check pass with new layout

Why: Must confirm no type errors or build failures from the layout restructuring before marking slice complete.

Do:
1. Run svelte-check in frontend/ directory
2. Run vite build in frontend/ directory
3. Fix any import errors, missing types, or broken references

Done when: Both svelte-check and vite build exit 0 with no errors.

## Inputs

- `frontend/src/lib/components/app-shell/dock.svelte`
- `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`
- `frontend/src/lib/components/app-shell/app-shell.svelte`
- `frontend/src/app.html`

## Expected Output

- `frontend/src/lib/components/app-shell/app-shell.svelte`
- `frontend/src/lib/components/app-shell/dock.svelte`
- `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`

## Verification

cd frontend && npx svelte-check --threshold error && npx vite build
