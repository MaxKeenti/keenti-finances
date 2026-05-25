---
id: S02
parent: M002
milestone: M002
provides:
  - Category color column via Flyway V7 migration
  - Theme infrastructure with runtime matchMedia switching via theme.svelte.ts
  - CategoryBadge component rendering OKLCH hue with theme-adaptive lightness/chroma
  - SwatchPicker component with direction-filtered hue palette
  - Dark/light theme switching via .dark class toggle — ready for S04 and S06 consumption
requires:
  []
affects:
  - S04
  - S06
key_files:
  - backend/src/main/resources/db/migration/V7__add_color_to_category.sql
  - backend/src/main/java/com/keenti/finances/domain/model/Category.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java
  - frontend/src/lib/theme.svelte.ts
  - frontend/src/routes/+layout.svelte
  - frontend/src/lib/components/ui/category-badge/category-badge.svelte
  - frontend/src/lib/components/ui/category-badge/index.ts
  - frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte
  - frontend/src/lib/components/ui/swatch-picker/index.ts
  - frontend/src/routes/categories/+page.svelte
  - frontend/src/routes/categories/+page.server.ts
  - frontend/src/routes/transactions/+page.svelte
  - frontend/src/routes/transactions/+page.server.ts
key_decisions:
  - color stored as VARCHAR(10) — sufficient for short OKLCH hue values without over-engineering the column type; nullable in DB so existing categories are unaffected by V7 migration
  - isDark exposed as getIsDark() getter rather than direct $state export — required by Svelte 5 state_invalid_export constraint; propagated to CategoryBadge and SwatchPicker
  - initTheme() called at layout top level and returns cleanup via onMount return value — ensures single listener per page load
  - SwatchPicker emits empty string for Clear; page.svelte converts '' to undefined to keep $form.color typed as string|undefined
  - vite build --root is not a valid CLI flag in vite v8 — correct invocation is cd frontend && npx vite build (not npx vite build --root frontend)
  - CategoryResource.java required unlisted updates because it constructs domain objects and DTOs directly alongside the planned files
patterns_established:
  - OKLCH category badge pattern: CategoryBadge component takes hue, name, direction props; uses getIsDark() reactive getter to compute theme-adaptive L/C values at render time
  - Direction-constrained swatch picker: palette is filtered by category type (INGRESS/EGRESS/BOTH) at the component level — SwatchPicker receives direction prop and renders only the matching hue subset
  - Svelte 5 module-level $state exposure pattern: export getter functions (getIsDark) rather than reassignable state variables to satisfy state_invalid_export constraint
observability_surfaces:
  - none — UI-only slice; theme detection fails gracefully (defaults to light if matchMedia unavailable); color field is nullable so no categories break on missing color
drill_down_paths:
  - .gsd/milestones/M002/slices/S02/tasks/T01-SUMMARY.md
  - .gsd/milestones/M002/slices/S02/tasks/T02-SUMMARY.md
  - .gsd/milestones/M002/slices/S02/tasks/T03-SUMMARY.md
  - .gsd/milestones/M002/slices/S02/tasks/T04-SUMMARY.md
duration: ""
verification_result: passed
completed_at: 2026-05-17T01:52:08.322Z
blocker_discovered: false
---

# S02: Theme Detection & Category Colors

**App switches light/dark with system preference without flash; categories show OKLCH-colored badges in both themes; color persists via Flyway V7 migration and backend API.**

## What Happened

S02 delivered system theme detection and category color badges across the full stack in four tasks.

**T01 — Backend color field (Flyway V7 + hexagonal stack):** Added `color VARCHAR(10)` column via `V7__add_color_to_category.sql`. The field was threaded through the entire hexagonal architecture: `Category` domain model → `CategoryEntity` → `PanacheCategoryRepository` → `CategoryRequest`/`CategoryResponse` DTOs → `CategoryResource` → `TransactionResponse` (as `categoryColor`). Color is nullable in DB and optional in `CategoryRequest` so existing categories are unaffected. `CategoryResource.java` required unlisted updates because it constructs domain and response objects directly. Backend compile (`./mvnw compile`) passed: BUILD SUCCESS in under 2s.

**T02 — Runtime matchMedia theme listener:** Created `frontend/src/lib/theme.svelte.ts` with a `$state`-based `isDark` reactive variable and a `matchMedia('prefers-color-scheme: dark')` change listener that toggles `.dark` on `<html>` at runtime. `initTheme()` is called from `+layout.svelte`'s script block and returns a cleanup function via `onMount`. The Svelte 5 `state_invalid_export` constraint required exposing `isDark` as a `getIsDark()` getter rather than direct export — documented in task summary and carried through to downstream components.

**T03 — CategoryBadge and SwatchPicker components:** Built `CategoryBadge` (OKLCH pill rendering hue with theme-adaptive L/C values; uses `getIsDark()` reactive getter) and `SwatchPicker` (direction-constrained hue circles: green-ish palette for INGRESS, red-complement for EGRESS, blue-ish for BOTH). Both components export from their `index.ts` files. SwatchPicker emits empty string for Clear; page.svelte converts `''` → `undefined` to keep `$form.color` typed as `string|undefined`. Category form wired with swatch picker and server actions updated to pass color to API. Discovered that `vite build --root` is not a valid CLI flag in vite v8 — correct invocation is `cd frontend && npx vite build`.

**T04 — Badge wiring into pages:** `CategoryBadge` rendered in the categories list (`hue={cat.color ?? null}`) and transactions table (`hue={tx.categoryColor ?? null}`). `categoryColor` field added to the frontend `Transaction` type, mapping directly to the `TransactionResponse` field from T01. Transactions render an em-dash for uncategorized rows (no badge), consistent with the prior plain-text fallback.

## Verification

Verified on `milestone/M002` branch via temp worktree (`/tmp/m002-verify`):

1. **V7 migration exists:** `backend/src/main/resources/db/migration/V7__add_color_to_category.sql` — `ALTER TABLE category ADD COLUMN color VARCHAR(10)` ✓
2. **CategoryResponse includes color:** `public record CategoryResponse(Long id, String name, String type, String color) {}` ✓
3. **TransactionResponse includes categoryColor:** `String categoryColor` field present ✓
4. **theme.svelte.ts exists with matchMedia listener:** file confirmed with `$state isDark`, `getIsDark()`, `initTheme()` ✓
5. **CategoryBadge component:** `frontend/src/lib/components/ui/category-badge/category-badge.svelte` — uses `getIsDark()` for OKLCH theme-adaptive rendering ✓
6. **SwatchPicker component:** `frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte` — direction-filtered hue circles ✓
7. **Categories page shows badges:** `<CategoryBadge hue={cat.color ?? null} name={cat.name} direction={cat.type} />` ✓
8. **Transactions page shows badges:** `<CategoryBadge hue={tx.categoryColor ?? null} name={tx.categoryName} direction={tx.direction} />` ✓
9. **Backend compile:** `bash backend/mvnw compile -f backend/pom.xml` → BUILD SUCCESS in 1.875s ✓
10. **Vite build:** `cd frontend && npx vite build` → ✔ done (exit 0); circular dependency warnings are pre-existing node_modules issues ✓
11. **svelte-check --threshold error:** 0 new errors introduced by S02; 11 pre-existing errors in `subscriptions/+page.svelte` (unrelated, documented in T02) ✓

## Requirements Advanced

None.

## Requirements Validated

- R004 — theme.svelte.ts matchMedia listener toggles .dark on html at load and on runtime change; initTheme() wired into +layout.svelte; vite build exits 0 on milestone/M002
- R005 — CategoryBadge renders OKLCH pill in categories list and transactions table; SwatchPicker constrains palette by direction; color persists via V7 migration and CategoryResponse/TransactionResponse DTOs

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

["CategoryResource.java required updates not listed in T01 task plan — it constructs domain and response objects directly and needed color wiring alongside listed files", "vite build --root frontend is not valid in vite v8 — all tasks used cd frontend && npx vite build instead", "isDark must be exposed as getIsDark() getter (not direct export) due to Svelte 5 state_invalid_export constraint discovered at build time in T02; this pattern propagated to T03 components"]

## Known Limitations

None.

## Follow-ups

None.

## Files Created/Modified

None.
