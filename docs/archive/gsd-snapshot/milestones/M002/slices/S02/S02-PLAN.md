# S02: Theme Detection & Category Colors

**Goal:** App switches light/dark with system preference without flash; categories show colored badges with direction-constrained OKLCH hues in both themes; color persists via backend API.
**Demo:** App switches light/dark with system preference without flash; categories show colored badges with direction-constrained hues in both themes

## Must-Haves

- ./mvnw compile -f backend/pom.xml exits 0; V7 migration exists; CategoryResponse includes color; TransactionResponse includes categoryColor; theme.svelte.ts exists and layout imports it; CategoryBadge renders colored pills; SwatchPicker shows direction-filtered hues; categories and transactions pages show badges; npx vite build exits 0; npx svelte-check --threshold error reports 0 new errors.

## Proof Level

- This slice proves: integration

## Verification

- Run the task and slice verification checks for this slice.

## Tasks

- [x] **T01: Add color column via Flyway migration and wire through backend stack** `est:45m`
  The category color field must persist in the database and flow through the entire hexagonal architecture so the frontend can receive and submit hue values.
  - Files: `backend/src/main/resources/db/migration/V7__add_color_to_category.sql`, `backend/src/main/java/com/keenti/finances/domain/model/Category.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`, `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java`
  - Verify: ./mvnw compile -f backend/pom.xml

- [x] **T02: Add runtime matchMedia change listener for system theme switching** `est:20m`
  S01 planted the inline script that sets .dark on load, but if the user changes system theme while the app is open, nothing happens. A reactive listener completes the theme-switching requirement.
  - Files: `frontend/src/lib/theme.svelte.ts`, `frontend/src/routes/+layout.svelte`
  - Verify: npx vite build --root frontend

- [x] **T03: Create CategoryBadge component with swatch picker and wire into category form** `est:1h`
  The core visual deliverable of this slice — a badge that renders OKLCH hue with theme-adaptive L/C values, and a picker that constrains palette by category direction.
  - Files: `frontend/src/lib/components/ui/category-badge/category-badge.svelte`, `frontend/src/lib/components/ui/category-badge/index.ts`, `frontend/src/lib/components/ui/swatch-picker/swatch-picker.svelte`, `frontend/src/lib/components/ui/swatch-picker/index.ts`, `frontend/src/routes/categories/+page.svelte`, `frontend/src/routes/categories/+page.server.ts`
  - Verify: npx vite build --root frontend

- [x] **T04: Display category badges in categories list and transactions table** `est:30m`
  Closes the visual loop — badges must appear wherever categories are shown so users see the color coding in context.
  - Files: `frontend/src/routes/categories/+page.svelte`, `frontend/src/routes/transactions/+page.svelte`, `frontend/src/routes/transactions/+page.server.ts`
  - Verify: npx svelte-check --threshold error --workspace frontend

## Files Likely Touched

- backend/src/main/resources/db/migration/V7__add_color_to_category.sql
- backend/src/main/java/com/keenti/finances/domain/model/Category.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java
- backend/src/main/java/com/keenti/finances/application/service/CategoryService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/TransactionResponse.java
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
