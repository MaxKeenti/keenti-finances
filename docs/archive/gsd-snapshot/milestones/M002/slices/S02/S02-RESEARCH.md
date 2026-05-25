# S02 Research: Theme Detection & Category Colors

**Depth:** Targeted research — known technologies (OKLCH, Tailwind dark mode, Flyway), moderately complex integration across backend and frontend.

## Summary

S01 already planted the inline `prefers-color-scheme` script in `app.html` that toggles `.dark` on `<html>` before first paint. The CSS architecture (`@custom-variant dark (&:is(.dark *))`) and OKLCH variable sets in `layout.css` are fully operational. S02's theme work is mostly wiring a `matchMedia` listener for runtime changes (system toggle while app is open). The larger effort is the category color system: backend migration, domain/entity/DTO changes, and a new frontend badge component that renders OKLCH hues with theme-adaptive lightness/chroma.

## Recommendation

1. Start with the Flyway migration + backend model changes (lowest risk, unblocks everything)
2. Build the category-badge component as an isolated Svelte component consuming hue + theme
3. Wire the swatch picker into the existing category create/edit dialog
4. Display badges in categories page and transactions page

## Implementation Landscape

### Theme Detection (already mostly done by S01)

**`frontend/src/app.html`** (lines 8-12) — Confirmed inline script:
```javascript
if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
  document.documentElement.classList.add('dark');
}
```

**Remaining work:** Add a `matchMedia.addEventListener('change', ...)` listener so the app reacts to system theme changes at runtime without reload. This should live in the root layout or a small reactive module.

**`frontend/src/routes/layout.css`** — OKLCH variables confirmed:
- Line 7: `@custom-variant dark (&:is(.dark *));`
- Lines 9-42: Light theme OKLCH vars
- Lines 44-76: Dark theme overrides under `.dark` selector
- Lines 78-119: `@theme inline` block exposing vars to Tailwind

### Backend: Category Color Column

**Current schema** (`V2__create_category_and_contact_tables.sql`):
```sql
CREATE TABLE category (
    id   BIGSERIAL    PRIMARY KEY,
    name VARCHAR(255) UNIQUE NOT NULL,
    type VARCHAR(50)  NOT NULL CHECK (type IN ('INGRESS', 'EGRESS', 'BOTH'))
);
```

**Next migration:** V7 (latest is V6__update_admin_password.sql)

**Migration needed** (`V7__add_color_to_category.sql`):
```sql
ALTER TABLE category ADD COLUMN color VARCHAR(10);
```

Nullable — existing categories get null until user assigns colors. Frontend defaults to first swatch in palette when null.

**Files to modify:**
| File | Change |
|------|--------|
| `backend/.../domain/model/Category.java` | Add `String color` field |
| `backend/.../persistence/CategoryEntity.java` | Add `@Column color` field + getter/setter |
| `backend/.../persistence/PanacheCategoryRepository.java` | Update `toDomain()` and `toEntity()` mappers |
| `backend/.../rest/CategoryRequest.java` | Add `String color` (nullable) |
| `backend/.../rest/CategoryResponse.java` | Add `String color` |
| `backend/.../service/CategoryService.java` | Pass color through create/update |

### Frontend: Category Color Badge Component

**Decision from M002-CONTEXT:** Store OKLCH hue value (e.g. "145"). Frontend renders with fixed lightness/chroma per theme:
- Light mode: high lightness (~0.92) + medium chroma (~0.05)  
- Dark mode: lower lightness (~0.3) + higher chroma (~0.08)

**New component:** `frontend/src/lib/components/ui/category-badge.svelte`
- Props: `hue: string | null`, `name: string`, `direction?: string`
- Renders: pill/badge with computed `background: oklch(L C H)` where L/C vary by `.dark` ancestor
- Fallback: neutral gray badge when hue is null

**Curated swatch palette** (direction-constrained):
- INGRESS: greens (90-160 hue range)
- EGRESS: warm reds/oranges (0-60, 320-360 hue range)
- BOTH: blues/purples (200-280 hue range)

### Frontend: Swatch Picker in Category Form

**Current form location:** `frontend/src/routes/categories/+page.svelte` — uses dialog with name + type fields.

**Add:** Swatch picker after type select. Shows 6-8 curated hue circles filtered by selected direction. Clicking a swatch sets the color value. Selected swatch gets a ring indicator.

**Schema update:** `frontend/src/routes/categories/+page.server.ts` — add `color` to Zod schema (optional string) and Category type definition.

### Frontend: Display Badges

**Categories page** (`frontend/src/routes/categories/+page.svelte`):
- Replace or augment existing `Badge` with `category-badge` showing the color swatch next to name

**Transactions page** (`frontend/src/routes/transactions/+page.svelte`):
- Line 163 currently shows `{tx.categoryName ?? '—'}` as plain text
- Replace with `<CategoryBadge>` component
- Needs category color data — either include in transaction response or join from categories list (already loaded separately)

## Natural Seams (Task Decomposition)

1. **Backend migration + model** — V7 migration, Category domain/entity/request/response/service changes
2. **Theme runtime listener** — Small reactive module for matchMedia change events
3. **CategoryBadge component** — Isolated Svelte component, no API dependency for dev
4. **Swatch picker in category form** — Depends on backend accepting color field
5. **Wire badges into pages** — Categories page + transactions page display

## First Proof (Highest Risk / Biggest Unblocker)

The CategoryBadge component with correct OKLCH rendering in both themes. If the color math looks wrong in either theme, everything downstream fails visually. Build and visually verify this component first.

## Verification

- `npx vite build` — exit 0
- `npx svelte-check --threshold error` — 0 new errors
- Backend builds with `./mvnw compile` after migration + model changes
- Visual: category badge renders correct hue in light and dark mode
- Visual: swatch picker shows direction-appropriate colors

## Constraints

- No manual dark/light toggle — pure system-follow per architectural decision
- Direction constraint on palette is UX-only (not backend-enforced) — frontend filters swatches by selected type
- Existing 11 svelte-check errors in other files (native-date-picker, subscriptions page) are pre-existing and out of scope
