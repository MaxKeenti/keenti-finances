# S03: Category color picker upgrade — Research

**Date:** 2026-05-23

## Summary

S03 upgrades the category color picker from curated direction-constrained swatches to a full 360° hue wheel with hex input. The current system stores a numeric `hue` value (0-360) on categories and renders badges using OKLCH with fixed lightness/chroma per theme mode in `category-badge.svelte`. A `SwatchPicker` component provides the current color selection UI with preset hue values.

The backend stores hue as a numeric field — no schema changes needed. The work is purely frontend: replace the `SwatchPicker` with a 360° hue wheel + hex input component, add hex-to-OKLCH-hue conversion logic, and add live badge preview in both light and dark modes inline in the form. The existing `category-badge.svelte` rendering logic is preserved since it already uses OKLCH hue.

## Recommendation

Build a new `ColorPicker` component that combines: (1) a 360° hue wheel using an `<input type="range">` styled as a circular/linear gradient, (2) a hex input field with bidirectional conversion (hex → OKLCH hue, hue → representative hex), and (3) dual light/dark badge previews using the existing `category-badge.svelte` component. Replace `SwatchPicker` usage in the category create/edit form with this new component.

**Why:** The hue wheel is a simple range input over 0-360 with an OKLCH gradient background — no external library needed. Hex-to-hue conversion uses CSS Color Level 4's `oklch()` function which is natively supported in modern browsers. The conversion extracts only the hue component (discarding lightness/chroma) since badge rendering uses fixed L/C per theme.

## Implementation Landscape

### Key Files

- `frontend/src/lib/components/swatch-picker.svelte` — Current color picker component with preset swatches; will be replaced or extended
- `frontend/src/lib/components/category-badge.svelte` — OKLCH badge rendering; unchanged but reused for live preview
- `frontend/src/lib/components/color-picker.svelte` — **NEW**: 360° hue wheel + hex input + dual preview component
- `frontend/src/lib/utils/color.ts` — **NEW**: Hex-to-OKLCH-hue conversion utility (parse hex → RGB → OKLCH → extract hue)
- `frontend/src/routes/(app)/categories/` — Category create/edit forms that currently use SwatchPicker; switch to new ColorPicker
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java` — Has `hue` field (numeric); no change needed
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java` — Has `hue` field; no change needed

### Build Order

1. **Color conversion utility** (`color.ts`) — Implement `hexToOklchHue(hex: string): number` and `oklchHueToRepresentativeHex(hue: number): string`. This is the core logic and can be unit-tested independently. Use the `culori` library if already installed, otherwise implement manually using the standard sRGB→OKLCH matrix math.
2. **ColorPicker component** — Build the new component with hue slider (0-360 range input with OKLCH gradient), hex input with validation and bidirectional binding, and dual badge previews (light + dark) using `category-badge.svelte`.
3. **Form integration** — Replace `SwatchPicker` with `ColorPicker` in category create and edit forms. The `hue` binding stays the same — only the picker UI changes.

### Patterns and Constraints

- **OKLCH hue is the stored value** — The backend stores integer hue (0-360). Hex input is a convenience that converts to hue. The hue is the source of truth.
- **Badge rendering unchanged** — `category-badge.svelte` uses `oklch(L C H)` with fixed L/C per theme. Only H comes from the category. This slice changes how H is selected, not how it's rendered.
- **Hex → hue is lossy** — Many different hex values map to the same OKLCH hue (differing only in lightness/chroma). The hex input is an intuitive entry point, but the badge will render with the theme's fixed L/C at that hue. The preview shows exactly how it will look.
- **CSS gradient for hue wheel** — Use `background: linear-gradient(to right, oklch(0.7 0.15 0), oklch(0.7 0.15 60), ..., oklch(0.7 0.15 360))` on the range input track for a perceptually uniform hue strip.
- **No external color picker library needed** — A range input + hex text input is simpler and more maintainable than a full color picker widget. The interaction is: slide for hue, type hex for precision.
- **Svelte 5 runes** — Use `$state` and `$derived` for reactive hue/hex binding per project conventions.

### Verification

- `npm run build` — Frontend builds with no type errors
- `npm run check` — Svelte check passes
- Visual test: Open category create form → see hue slider and hex input → slide to different hues → verify badge preview updates in both light and dark → type `#FF5733` → verify hue updates and badge preview matches → save → verify badge on categories list matches preview
- Edge cases: pure white (#FFFFFF), pure black (#000000), grays (#808080) — these have undefined/zero chroma in OKLCH; verify graceful handling (default to hue 0 or maintain previous hue)
