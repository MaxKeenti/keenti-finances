---
estimated_steps: 43
estimated_files: 4
skills_used: []
---

# T03: SvelteKit Contacts CRUD page with form validation, list view, and edit/delete dialogs

---
estimated_steps: 5
estimated_files: 4
skills_used:
  - svelte-code-writer
  - svelte-core-bestpractices
---

# T03: SvelteKit Contacts CRUD page with form validation, list view, and edit/delete dialogs

**Slice:** S02 — Categories & Contacts
**Milestone:** M001

## Description

Create the /contacts route with a full CRUD UI following the same patterns as /categories. The page loads contacts from the Quarkus API via the SvelteKit proxy, displays them in a shadcn Table (Name, Phone, Email, Actions), and provides a dialog form for creating and editing contacts. Delete uses a confirmation dialog. Form validation uses Zod schema with superforms. Toast notifications via sonner.

Also adds Contacts nav item (with Users icon) to both sidebar.svelte and bottom-nav.svelte.

## Steps

1. Create `frontend/src/routes/contacts/+page.server.ts`: load function fetches GET /api/contacts via the proxy, returns { contacts }. Define actions for create (POST), update (PUT), and delete (DELETE) that forward to the Quarkus API
2. Create `frontend/src/routes/contacts/+page.svelte`: display contacts in a shadcn Table with columns (Name, Phone, Email, Actions). Dialog form with Zod schema: name (required), phone (optional), email (optional, email format when provided). Same create/edit/delete dialog pattern as categories.
3. Update `frontend/src/lib/components/app-shell/sidebar.svelte`: add Contacts nav item with Users icon
4. Update `frontend/src/lib/components/app-shell/bottom-nav.svelte`: add Contacts nav item
5. Run `bun run check` to verify 0 type errors

## Must-Haves

- [ ] Contacts load on page visit via server-side load function
- [ ] Create, edit, delete all work through form actions proxied to Quarkus
- [ ] Zod validation on name (required), email (optional but valid format), phone (optional)
- [ ] Toast notifications on success and error
- [ ] Nav items added to both sidebar and bottom-nav

## Verification

- `bun run check` exits with 0 errors
- `test -f frontend/src/routes/contacts/+page.svelte`
- `test -f frontend/src/routes/contacts/+page.server.ts`
- `grep -q 'contacts' frontend/src/lib/components/app-shell/sidebar.svelte`

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java` — API contract reference
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java` — request shape
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java` — response shape
- `frontend/src/routes/categories/+page.server.ts` — pattern reference from T02
- `frontend/src/routes/categories/+page.svelte` — pattern reference from T02
- `frontend/src/lib/components/app-shell/sidebar.svelte` — nav to update (already modified by T02)
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — nav to update (already modified by T02)

## Expected Output

- `frontend/src/routes/contacts/+page.server.ts` — server load + form actions
- `frontend/src/routes/contacts/+page.svelte` — contacts CRUD UI
- `frontend/src/lib/components/app-shell/sidebar.svelte` — updated with Contacts nav item
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — updated with Contacts nav item

## Inputs

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactRequest.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/ContactResponse.java`
- `frontend/src/routes/categories/+page.server.ts`
- `frontend/src/routes/categories/+page.svelte`
- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`

## Expected Output

- `frontend/src/routes/contacts/+page.server.ts`
- `frontend/src/routes/contacts/+page.svelte`
- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`

## Verification

bun run check && test -f frontend/src/routes/contacts/+page.svelte && grep -q 'contacts' frontend/src/lib/components/app-shell/sidebar.svelte
