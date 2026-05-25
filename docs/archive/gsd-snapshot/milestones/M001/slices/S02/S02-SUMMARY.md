---
id: S02
parent: M001
milestone: M001
provides:
  - Category domain model and CRUD REST API at /api/categories
  - Contact domain model and CRUD REST API at /api/contacts
  - Flyway V2 migration (category and contact tables)
  - SvelteKit /categories CRUD page with form validation and toast notifications
  - SvelteKit /contacts CRUD page with form validation and toast notifications
  - Established SvelteKit CRUD pattern: single schema + dynamic action + superforms create/update + plain enhance delete
  - Categories and Contacts nav items in sidebar and bottom-nav
requires:
  - slice: S01
    provides: hexagonal package structure, SvelteKit proxy, auth guard, app shell sidebar/bottom-nav, shadcn-svelte components
affects:
  - S03
  - S05
  - S06
key_files:
  - backend/src/main/resources/db/migration/V2__create_category_and_contact_tables.sql
  - backend/src/main/java/com/keenti/finances/domain/model/Category.java
  - backend/src/main/java/com/keenti/finances/domain/model/Contact.java
  - backend/src/main/java/com/keenti/finances/application/service/CategoryService.java
  - backend/src/main/java/com/keenti/finances/application/service/ContactService.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java
  - backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java
  - frontend/src/routes/categories/+page.server.ts
  - frontend/src/routes/categories/+page.svelte
  - frontend/src/routes/contacts/+page.server.ts
  - frontend/src/routes/contacts/+page.svelte
  - frontend/src/lib/components/app-shell/sidebar.svelte
  - frontend/src/lib/components/app-shell/bottom-nav.svelte
  - frontend/src/lib/components/app-shell/app-shell.svelte
key_decisions:
  - Category type validation enforced in application service (not DB constraint) for structured 400 error before hitting DB
  - Contacts allow duplicate names by domain intent — no existsByName check on ContactRepository
  - Duplicate category name on update checks for name change before throwing 409 — same-name PUT is allowed
  - Single Zod schema for create and update; form action attribute switches at submit time
  - Delete uses plain formData + native enhance (no superforms) since delete has no validation
  - Sonner Toaster mounted in app-shell.svelte (not root layout) — only renders for authenticated sessions
  - Bottom-nav uses overflow-x-auto + min-w-[64px] to fit 6 nav items without overflow
  - Contact email Zod: .optional().or(z.literal('')) allows empty input while validating format when present
  - Empty phone/email coerced to null before Quarkus request to match nullable ContactRequest fields
patterns_established:
  - Full CRUD vertical slice: domain POJO → port interfaces → application service → Panache adapter → JAX-RS resource → SvelteKit server action → Svelte page
  - SvelteKit CRUD page pattern: single schema, dynamic action, superforms create/update, plain enhance delete, Dialog for edit/delete
  - Structured JSON error bodies (400/404/409) from JAX-RS resources with JBoss Logger structured logging
observability_surfaces:
  - JBoss Logger structured log lines on every CRUD operation (entity type, operation, id) in CategoryService and ContactService
  - REST resources return structured JSON error bodies with HTTP 400 (validation), 404 (not found), 409 (conflict)
  - SvelteKit pages surface success/failure via sonner toast notifications
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-13T20:57:42.436Z
blocker_discovered: false
---

# S02: S02: Categories & Contacts

**Full CRUD for Category and Contact through SvelteKit UI → Quarkus hexagonal backend → PostgreSQL, with Flyway V2 migration, form validation, toast notifications, and nav items in sidebar and bottom-nav.**

## What Happened

S02 delivered two new domain models (Category, Contact) and their complete vertical stacks across three tasks.

**T01 — Backend hexagonal stack:** A Flyway V2 migration creates `category` (id, name, type, created_at) and `contact` (id, name, phone, email, created_at) tables. The domain layer defines clean POJOs with no framework imports. Port interfaces (CategoryUseCase, ContactUseCase, CategoryRepository, ContactRepository) define the hexagonal boundaries. Application services enforce business rules — category type validation (INGRESS/EGRESS/BOTH) returns a structured 400 before touching the DB; duplicate category name detection on both create and update returns a 409; contacts allow duplicate names by domain intent. Panache adapters implement the repository ports. JAX-RS REST resources expose all four CRUD operations at /api/categories and /api/contacts, returning structured JSON error bodies (400, 404, 409). `./mvnw compile -q` exits 0; domain layer confirmed framework-free.

**T02 — Categories UI:** The `/categories` SvelteKit route uses a single `categorySchema` (name, type enum) for both create and update, switching the form action dynamically. Edit/delete operations use shadcn-svelte Dialog components. Delete uses plain formData + native enhance (no superforms, no validation needed). Sonner toast notifications are mounted in app-shell.svelte (not root layout) so they only appear in authenticated sessions. The bottom-nav uses `overflow-x-auto + min-w-[64px]` to fit 6 items. `bun run check` passes with 0 errors, 3 pre-existing superforms warnings.

**T03 — Contacts UI:** The `/contacts` route mirrors the categories pattern. The email Zod field uses `.optional().or(z.literal(''))` to accept empty input while validating format when present. Empty phone/email strings are coerced to `null` before the Quarkus request to match the nullable `ContactRequest` fields. `bun run check` passes with 0 errors.

## Verification

1. `./mvnw compile -q` — exit 0 (backend compiles clean). 2. Domain layer framework-import grep returned exit 1 (no matches — domain is framework-free). 3. `bun run check` — exit 0, 0 errors, 3 pre-existing superforms warnings. 4. All file existence checks pass: `frontend/src/routes/categories/+page.svelte`, `+page.server.ts`, `frontend/src/routes/contacts/+page.svelte`, `+page.server.ts`. 5. Nav presence confirmed: `grep -q 'categories'` and `grep -q 'contacts'` both pass for sidebar.svelte and bottom-nav.svelte.

## Requirements Advanced

None.

## Requirements Validated

None.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

bun install was required before bun run check could run in the worktree — node_modules are not shared across git worktrees. No structural deviations from the slice plan.

## Known Limitations

None.

## Follow-ups

None.

## Files Created/Modified

None.
