---
estimated_steps: 9
estimated_files: 2
skills_used: []
---

# T02: Add runtime matchMedia change listener for system theme switching

S01 planted the inline script that sets .dark on load, but if the user changes system theme while the app is open, nothing happens. A reactive listener completes the theme-switching requirement.

Do:
1. Create frontend/src/lib/theme.svelte.ts — a small Svelte 5 reactive module that:
   - On mount, registers matchMedia('(prefers-color-scheme: dark)').addEventListener('change', cb)
   - The callback adds/removes .dark on document.documentElement
   - Exports a reactive isDark state for components that need conditional logic
2. Import and initialize the theme module in frontend/src/routes/+layout.svelte (onMount or top-level $effect)
3. Ensure no flash — the inline script in app.html handles initial state; the listener only handles runtime changes

Done when: theme.svelte.ts exists; +layout.svelte imports it; npx vite build exits 0; npx svelte-check reports no errors in these files.

## Inputs

- `frontend/src/routes/+layout.svelte`

## Expected Output

- `frontend/src/lib/theme.svelte.ts`
- `frontend/src/routes/+layout.svelte`

## Verification

npx vite build --root frontend
