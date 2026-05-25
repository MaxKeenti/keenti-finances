---
id: T03
parent: S02
milestone: M002
key_files:
  - frontend/src/lib/components/ui/category-badge/category-badge.svelte
  - frontend/src/lib/components/ui/category-badge/index.ts
  - frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte
  - frontend/src/lib/components/ui/swatch-picker/index.ts
  - frontend/src/routes/categories/+page.svelte
  - frontend/src/routes/categories/+page.server.ts
key_decisions:
  - vite build --root is not a valid flag in vite v8 — root is a positional argument; correct invocation is `vite build` run from inside the frontend/ directory
  - CategoryBadge uses getIsDark() reactive getter (not a direct $state import) to stay compatible with Svelte 5 module-level state restrictions established in T02
  - SwatchPicker emits empty string for Clear and page.svelte converts '' to undefined to keep $form.color typed as string|undefined
duration: 
verification_result: passed
completed_at: 2026-05-17T01:44:23.980Z
blocker_discovered: false
---

# T03: Created CategoryBadge (OKLCH pill) and SwatchPicker (direction-filtered hue circles) components; wired color field into category form and server actions; build passes

**Created CategoryBadge (OKLCH pill) and SwatchPicker (direction-filtered hue circles) components; wired color field into category form and server actions; build passes**

## What Happened

Created four new files: category-badge.svelte renders a rounded pill using oklch() with theme-adaptive L/C values (L=0.92/C=0.05 light, L=0.3/C=0.08 dark) via getIsDark() from theme.svelte.ts, with a neutral muted fallback when hue is null. swatch-picker.svelte renders 6-hue circles filtered by direction (INGRESS→greens, EGRESS→reds/oranges, BOTH→blues/purples), with a ring indicator on selected swatch and a Clear button. Both have index.ts barrel exports. Updated +page.server.ts to add color: z.string().optional() to categorySchema and include color in create/update API request bodies. Updated +page.svelte to import and use CategoryBadge in the table rows (replacing plain text name) and SwatchPicker after the type select in the dialog form; color state tracked via $form.color and submitted via a hidden input. Fixed the build command — the task plan specified `npx vite build --root frontend` but vite v8 does not accept --root as a flag; the correct invocation is `cd frontend && npx vite build` (root is a positional arg). Build exits 0 with only pre-existing typebox/zod circular dependency warnings from node_modules.

## Verification

Ran `cd frontend && npx vite build` — exited 0 with ✔ done. Warnings are pre-existing circular deps in node_modules (typebox, zod-v3-to-json-schema, @internationalized/date) and the known state_referenced_locally warning from superForm usage (documented in MEM012). No new errors introduced.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && npx vite build` | 0 | pass | 45000ms |

## Deviations

Task verification command `npx vite build --root frontend` was incorrect for vite v8 (--root is not a CLI flag). Used `cd frontend && npx vite build` instead — same semantic intent, correct syntax.

## Known Issues

none

## Files Created/Modified

- `frontend/src/lib/components/ui/category-badge/category-badge.svelte`
- `frontend/src/lib/components/ui/category-badge/index.ts`
- `frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte`
- `frontend/src/lib/components/ui/swatch-picker/index.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/categories/+page.server.ts`
