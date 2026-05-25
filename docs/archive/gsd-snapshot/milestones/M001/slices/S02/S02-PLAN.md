# S02: Categories & Contacts

**Goal:** Create, edit, and delete categories and contacts through the UI; data persists across sessions via Quarkus hexagonal backend with PostgreSQL
**Demo:** Create, edit, and delete categories and contacts through the UI; data persists across sessions

## Must-Haves

- Flyway V2 migration creates category and contact tables, `./mvnw compile -q` exits 0\n- GET/POST/PUT/DELETE /api/categories and /api/contacts return correct JSON through the SvelteKit proxy\n- Categories UI at /categories lists all categories, supports create/edit/delete with form validation\n- Contacts UI at /contacts lists all contacts, supports create/edit/delete with form validation\n- Both sidebar and bottom-nav include Categories and Contacts nav items\n- `bun run check` passes with 0 errors\n- Category has name (required) and type (INGRESS, EGRESS, or BOTH)\n- Contact has name (required), phone (optional), email (optional)

## Proof Level

- This slice proves: integration — full CRUD through SvelteKit proxy to Quarkus to PostgreSQL, verified by compile + type-check

## Integration Closure

Upstream surfaces consumed: hexagonal package structure, SvelteKit proxy (`api/[...path]/+server.ts`), auth guard (`hooks.server.ts`), app shell sidebar/bottom-nav, shadcn-svelte UI components (card, dialog, form, input, button, table, label, select). New wiring: two new REST resources registered at /api/categories and /api/contacts, two new SvelteKit route groups (/categories, /contacts), nav items added to sidebar and bottom-nav. Remains: transactions (S03), dashboard (S04), subscriptions (S05), debts (S06) all depend on Category/Contact models produced here.

## Verification

- Application services log CRUD operations with structured Jboss Logger lines (entity type, operation, id). REST resources return structured JSON error bodies with appropriate HTTP status codes (400 for validation, 404 for not found, 409 for name conflicts). SvelteKit pages display toast notifications via sonner on success/failure.

## Tasks

- [x] **T01: Flyway V2 migration, Category and Contact domain models, hexagonal ports, services, and REST resources** `est:2h`
  ---
  estimated_steps: 8
  estimated_files: 18
  skills_used: []
  ---
  - Files: `backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql`, `backend/src/main/java/com/keenti/finances/domain/model/Category.java`, `backend/src/main/java/com/keenti/finances/domain/model/Contact.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java`, `backend/src/main/java/com/keenti/finances/application/service/CategoryService.java`, `backend/src/main/java/com/keenti/finances/application/service/ContactService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java`
  - Verify: ./mvnw compile -q && grep -r 'import jakarta\|import javax\|import io.quarkus' backend/src/main/java/com/keenti/finances/domain/ ; test $? -eq 1

- [x] **T02: SvelteKit Categories CRUD page with form validation, list view, and edit/delete dialogs** `est:1h30m`
  ---
  estimated_steps: 6
  estimated_files: 5
  skills_used:
    - svelte-code-writer
    - svelte-core-bestpractices
  ---
  - Files: `frontend/src/routes/categories/+page.server.ts`, `frontend/src/routes/categories/+page.svelte`, `frontend/src/lib/components/app-shell/sidebar.svelte`, `frontend/src/lib/components/app-shell/bottom-nav.svelte`
  - Verify: bun run check && test -f frontend/src/routes/categories/+page.svelte && grep -q 'categories' frontend/src/lib/components/app-shell/sidebar.svelte

- [x] **T03: SvelteKit Contacts CRUD page with form validation, list view, and edit/delete dialogs** `est:1h`
  ---
  estimated_steps: 5
  estimated_files: 4
  skills_used:
    - svelte-code-writer
    - svelte-core-bestpractices
  ---
  - Files: `frontend/src/routes/contacts/+page.server.ts`, `frontend/src/routes/contacts/+page.svelte`, `frontend/src/lib/components/app-shell/sidebar.svelte`, `frontend/src/lib/components/app-shell/bottom-nav.svelte`
  - Verify: bun run check && test -f frontend/src/routes/contacts/+page.svelte && grep -q 'contacts' frontend/src/lib/components/app-shell/sidebar.svelte

## Files Likely Touched

- backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql
- backend/src/main/java/com/keenti/finances/domain/model/Category.java
- backend/src/main/java/com/keenti/finances/domain/model/Contact.java
- backend/src/main/java/com/keenti/finances/domain/port/in/CategoryUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/in/ContactUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/CategoryRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/ContactRepository.java
- backend/src/main/java/com/keenti/finances/application/service/CategoryService.java
- backend/src/main/java/com/keenti/finances/application/service/ContactService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/CategoryEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/ContactEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheCategoryRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheContactRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java
- frontend/src/routes/categories/+page.server.ts
- frontend/src/routes/categories/+page.svelte
- frontend/src/lib/components/app-shell/sidebar.svelte
- frontend/src/lib/components/app-shell/bottom-nav.svelte
- frontend/src/routes/contacts/+page.server.ts
- frontend/src/routes/contacts/+page.svelte
