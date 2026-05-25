---
estimated_steps: 21
estimated_files: 6
skills_used: []
---

# T03: Create CategoryBadge component with swatch picker and wire into category form

The core visual deliverable of this slice — a badge that renders OKLCH hue with theme-adaptive L/C values, and a picker that constrains palette by category direction.

Do:
1. Create frontend/src/lib/components/ui/category-badge/category-badge.svelte:
   - Props: hue (string|null), name (string), direction (string, optional)
   - Renders a pill/badge with inline style background: oklch(L C H/360) where L=0.92/C=0.05 in light, L=0.3/C=0.08 in dark
   - Text color: dark text in light mode, light text in dark mode
   - Fallback: neutral muted badge when hue is null
2. Create frontend/src/lib/components/ui/category-badge/index.ts barrel export
3. Create frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte:
   - Props: direction (INGRESS|EGRESS|BOTH), value (string|null), onchange callback
   - Renders 6-8 curated hue circles filtered by direction:
     INGRESS: [100, 120, 140, 150, 160, 170] (greens)
     EGRESS: [10, 20, 30, 40, 350, 0] (reds/oranges)
     BOTH: [220, 240, 260, 270, 280, 300] (blues/purples)
   - Selected swatch gets ring-2 indicator
   - Clicking calls onchange with hue string
4. Create frontend/src/lib/components/ui/swatch-picker/index.ts barrel export
5. Wire swatch picker into frontend/src/routes/categories/+page.svelte category dialog after type select
6. Add color field to categorySchema in +page.server.ts (z.string().optional())
7. Include color in form submission body for create and update actions

Done when: CategoryBadge renders a colored pill; SwatchPicker shows direction-filtered circles; category form submits color; npx vite build exits 0.

## Inputs

- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/categories/+page.server.ts`
- `frontend/src/lib/theme.svelte.ts`

## Expected Output

- `frontend/src/lib/components/ui/category-badge/category-badge.svelte`
- `frontend/src/lib/components/ui/category-badge/index.ts`
- `frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte`
- `frontend/src/lib/components/ui/swatch-picker/index.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/routes/categories/+page.server.ts`

## Verification

npx vite build --root frontend
