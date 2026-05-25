# S04: User theme and typography settings — Research

**Date:** 2026-05-23

## Summary

S04 adds per-user theme customization: primary color (OKLCH hue) and heading/body font presets, stored on the `app_user` table and applied via CSS variables. Currently `UserEntity` has 4 columns (`id`, `username`, `passwordHash`, `workosId`) — three new columns are needed: `primary_hue`, `heading_font`, `body_font`. No Settings page exists yet.

The frontend theme system in `layout.css` is already fully OKLCH-based with `@theme inline` defining `--font-sans` (Geist) and `--font-heading` (Fraunces) as Tailwind tokens. Three font families are already installed as npm packages: `@fontsource-variable/geist`, `@fontsource-variable/fraunces`, and `@fontsource-variable/playfair-display` (Playfair is installed but currently unused). The existing `SwatchPicker` component from categories is directly reusable for the primary hue picker on the Settings page.

## Recommendation

Add a Flyway V12 migration for the three preference columns on `app_user` with sensible defaults (hue 91 matching current amber/gold, Geist for sans, Fraunces for heading). Create a `/api/user/preferences` REST endpoint for GET/PUT. Load preferences in the root `+layout.server.ts` and apply them as CSS custom properties in `+layout.svelte`. Build the Settings page at `/(app)/settings/+page.svelte` with a hue picker (reuse SwatchPicker or the new ColorPicker from S03), and font dropdowns for heading and body fonts. Add Settings to the dock navigation.

**Why:** Storing preferences on `app_user` avoids an extra table and JOIN. Loading in `+layout.server.ts` ensures preferences are available on first render (no flash). CSS custom properties are the natural integration point since the theme system already uses them.

## Implementation Landscape

### Key Files

- `backend/src/main/resources/db/migration/V12__user_preferences.sql` — **NEW**: Add `primary_hue INTEGER DEFAULT 91`, `heading_font VARCHAR(50) DEFAULT 'Fraunces'`, `body_font VARCHAR(50) DEFAULT 'Geist'` to `app_user`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — Add `primaryHue`, `headingFont`, `bodyFont` fields with column mappings
- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — Add preference fields to domain model
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserPreferencesResource.java` — **NEW**: `GET /api/user/preferences` and `PUT /api/user/preferences` endpoints, scoped via UserContext
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/UserContext.java` — Can optionally carry preference data to avoid extra query, but a separate endpoint is cleaner
- `frontend/src/routes/(app)/+layout.server.ts` — Load user preferences on initial page load and pass to layout
- `frontend/src/routes/(app)/+layout.svelte` — Apply CSS custom properties (`--primary-hue`, `--font-heading`, `--font-body`) from loaded preferences
- `frontend/src/routes/(app)/settings/+page.svelte` — **NEW**: Settings page with primary hue picker, heading font dropdown, body font dropdown, live preview
- `frontend/src/routes/(app)/settings/+page.ts` — **NEW**: Load current preferences
- `frontend/src/lib/components/dock.svelte` — Add Settings link to dock navigation
- `frontend/src/routes/layout.css` — Update `@theme inline` to reference CSS custom properties instead of hardcoded values for hue and fonts
- `frontend/src/app.html` — Ensure all font presets are imported (Geist, Fraunces, Playfair Display already installed as @fontsource-variable packages)

### Build Order

1. **V12 migration + UserEntity columns** (backend foundation) — Add the three preference columns with defaults. Extend UserEntity and domain model. This is the data layer prerequisite.
2. **Preferences REST endpoint** — Create UserPreferencesResource with GET/PUT. UserContext provides the user ID for scoping. This enables frontend integration.
3. **Layout CSS variable integration** — Modify `layout.css` to use CSS custom properties for primary hue and fonts. Update `+layout.server.ts` to load preferences and `+layout.svelte` to set the CSS custom properties. At this point, changing DB values changes the theme.
4. **Settings page** — Build the UI with hue picker (reuse SwatchPicker or ColorPicker from S03 if available), font dropdowns, and live preview. Add to dock. Wire PUT calls to persist changes.

### Patterns and Constraints

- **Font preset list:** Three fonts already installed — Geist (sans), Fraunces (serif heading), Playfair Display (serif alternative). Sufficient for a meaningful selection. Additional fonts can be added later.
- **CSS variable application:** Set `--primary-hue`, `--font-heading`, `--font-body` on `:root` or `<body>` from `+layout.svelte`. The `layout.css` `@theme inline` block references these variables instead of hardcoded values.
- **Migration version:** V11 is reserved for S02 soft-delete. This slice uses V12.
- **UserContext optimization:** Preferences could be loaded eagerly in UserScopeFilter and cached on UserContext to avoid a second DB query per request. However, preferences are only needed on page load (SSR), so a separate endpoint called from `+layout.server.ts` is simpler and sufficient.
- **No S03 dependency:** S04 can proceed independently of S03. If S03's ColorPicker is available, the Settings page can reuse it for the primary hue picker. Otherwise, the existing SwatchPicker works fine.
- **Font preloading:** Fonts are already imported via @fontsource-variable packages in the CSS. Verify all three are imported in `layout.css` or component-level CSS. The `app.html` preload links ensure no FOUT.
- **Svelte 5 runes:** Use `$state` for preference form state, `$effect` for applying CSS variables reactively.

### Verification

- `./mvnw test` — Backend tests pass with new migration and endpoint
- `npm run build` — Frontend builds with no type errors
- Manual test: Open Settings page → change primary hue → verify app color updates immediately → change heading font → verify headings update → save → refresh page → verify preferences persisted → log out and back in → verify preferences still applied
- Verify defaults: New user (no preferences set) should see the current default theme (hue 91, Geist, Fraunces)
