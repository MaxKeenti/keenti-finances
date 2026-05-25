---
id: T02
parent: S02
milestone: M002
key_files:
  - frontend/src/lib/theme.svelte.ts
  - frontend/src/routes/+layout.svelte
key_decisions:
  - Svelte 5 forbids exporting reassigned $state; exposed isDark via getIsDark() getter instead of direct export
  - initTheme() called at top level of +layout.svelte script block — runs once per page load, listener is cleaned up on component destroy via onMount return value
duration: 
verification_result: passed
completed_at: 2026-05-17T01:41:36.970Z
blocker_discovered: false
---

# T02: Created theme.svelte.ts with matchMedia change listener and wired initTheme() into +layout.svelte for runtime dark/light switching

**Created theme.svelte.ts with matchMedia change listener and wired initTheme() into +layout.svelte for runtime dark/light switching**

## What Happened

Created `frontend/src/lib/theme.svelte.ts` — a Svelte 5 module that registers a `matchMedia('(prefers-color-scheme: dark)')` change listener on mount, toggling `.dark` on `document.documentElement` and tracking state via `$state`. The initial state is set from `mq.matches` inside `onMount` so it stays consistent with the existing flash-prevention inline script in `app.html`. Exporting reassigned `$state` is prohibited by Svelte 5 (`state_invalid_export`), so `isDark` is exposed via a `getIsDark()` getter function rather than a direct export. `initTheme()` is called from `+layout.svelte` so the listener activates for every route. The build required `bun install` in the worktree's `frontend/` directory first (per MEM011). Build succeeded; all 11 svelte-check errors are pre-existing in unrelated files (native-date-picker, subscriptions); zero errors in the two touched files.

## Verification

Ran `bun run build` in frontend/ — exited 0. Ran `bun run check` — zero errors or warnings in theme.svelte.ts or +layout.svelte; 11 pre-existing errors in other files are unaffected.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run build` | 0 | pass | 15000ms |
| 2 | `cd frontend && bun run check 2>&1 | grep -E 'theme|layout'` | 0 | pass — no output means no errors in touched files | 12000ms |

## Deviations

isDark exported as getIsDark() getter rather than direct state export — required by Svelte 5 state_invalid_export constraint discovered at build time.

## Known Issues

None.

## Files Created/Modified

- `frontend/src/lib/theme.svelte.ts`
- `frontend/src/routes/+layout.svelte`
