---
estimated_steps: 47
estimated_files: 4
skills_used: []
---

# T02: SvelteKit Categories CRUD page with form validation, list view, and edit/delete dialogs

---
estimated_steps: 6
estimated_files: 5
skills_used:
  - svelte-code-writer
  - svelte-core-bestpractices
---

# T02: SvelteKit Categories CRUD page with form validation, list view, and edit/delete dialogs

**Slice:** S02 — Categories & Contacts
**Milestone:** M001

## Description

Create the /categories route with a full CRUD UI. The page loads categories from the Quarkus API via the SvelteKit proxy, displays them in a shadcn Table, and provides a dialog form for creating and editing categories. Delete uses a confirmation dialog. Form validation uses Zod schema with superforms. Toast notifications via sonner for success/error feedback. The page follows the responsive patterns from S01 (works at mobile and desktop widths).

Also adds Categories nav item (with Tag/Layers icon) to both sidebar.svelte and bottom-nav.svelte.

## Steps

1. Create `frontend/src/routes/categories/+page.server.ts`: load function fetches GET /api/categories via the proxy, returns { categories }. Define actions for create (POST), update (PUT), and delete (DELETE) that forward to the Quarkus API with appropriate error handling (409 duplicate, 404 not found)
2. Create `frontend/src/routes/categories/+page.svelte`: display categories in a shadcn Table with columns (Name, Type, Actions). Add button opens dialog with category form. Edit button in each row opens same dialog pre-filled. Delete button shows confirmation dialog. Use Zod schema: name (min 1), type (enum INGRESS/EGRESS/BOTH). Form uses superforms with formsnap Field/Control components. Show sonner toast on success/error.
3. Update `frontend/src/lib/components/app-shell/sidebar.svelte`: add Categories nav item with Layers icon between Dashboard and Transactions
4. Update `frontend/src/lib/components/app-shell/bottom-nav.svelte`: add Categories nav item (may need to restructure for 5+ items on mobile — use a scrollable or 'More' pattern, or keep the most important 4 items visible)
5. Run `bun run check` to verify 0 type errors

## Must-Haves

- [ ] Categories load on page visit via server-side load function
- [ ] Create, edit, delete all work through form actions proxied to Quarkus
- [ ] Zod validation on name (required) and type (INGRESS/EGRESS/BOTH enum)
- [ ] Toast notifications on success and error
- [ ] Responsive layout works on mobile and desktop
- [ ] Nav items added to both sidebar and bottom-nav

## Verification

- `bun run check` exits with 0 errors
- `test -f frontend/src/routes/categories/+page.svelte`
- `test -f frontend/src/routes/categories/+page.server.ts`
- `grep -q 'categories' frontend/src/lib/components/app-shell/sidebar.svelte`

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java` — API contract reference
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java` — request shape
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java` — response shape
- `frontend/src/routes/login/+page.server.ts` — pattern reference for server actions with superforms
- `frontend/src/routes/login/+page.svelte` — pattern reference for form UI with formsnap
- `frontend/src/lib/components/app-shell/sidebar.svelte` — nav to update
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — nav to update
- `frontend/src/routes/api/[...path]/+server.ts` — proxy reference
- `frontend/src/lib/components/ui/table/index.ts` — available table components
- `frontend/src/lib/components/ui/dialog/index.ts` — available dialog components

## Expected Output

- `frontend/src/routes/categories/+page.server.ts` — server load + form actions
- `frontend/src/routes/categories/+page.svelte` — categories CRUD UI
- `frontend/src/lib/components/app-shell/sidebar.svelte` — updated with Categories nav item
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — updated with Categories nav item

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/CategoryResponse.java`
- `frontend/src/routes/login/+page.server.ts`
- `frontend/src/routes/login/+page.svelte`
- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`
- `frontend/src/routes/api/[...path]/+server.ts`

## Expected Output

- `frontend/src/routes/categories/+page.server.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`

## Verification

bun run check && test -f frontend/src/routes/categories/+page.svelte && grep -q 'categories' frontend/src/lib/components/app-shell/sidebar.svelte
