# S02: S02: Categories & Contacts — UAT

**Milestone:** M001
**Written:** 2026-05-13T20:57:42.436Z

# UAT: S02 — Categories & Contacts

**UAT Type:** Manual functional verification against a running stack (SvelteKit dev server + Quarkus dev mode + PostgreSQL)

## Preconditions

1. PostgreSQL running and accessible (local dev or Docker)
2. `./mvnw quarkus:dev` running in `backend/` (Flyway applies V1 + V2 migrations on start)
3. `bun dev` running in `frontend/`
4. User is logged in (authenticated session cookie present)

---

## Categories

### Create a category
1. Navigate to `/categories`
2. Click **New Category**
3. Enter name `"Food"`, select type `EGRESS`, click **Save**
4. **Expected:** Row `"Food / EGRESS"` appears in the table; success toast shows

### Edit a category
5. Click the edit icon on the `"Food"` row
6. Change name to `"Groceries"`, click **Save**
7. **Expected:** Row updates to `"Groceries / EGRESS"`; success toast shows

### Duplicate name rejected
8. Create a second category named `"Groceries"` with any type
9. **Expected:** Error toast or inline error — name conflict (409)

### Delete a category
10. Click delete on `"Groceries"`
11. Confirm in the dialog
12. **Expected:** Row removed from table; success toast shows

### Type field required
13. Attempt to create a category with no name
14. **Expected:** Inline validation error — name is required; form not submitted

### Session persistence
15. Reload the page
16. **Expected:** All previously created categories still present

---

## Contacts

### Create a contact (all fields)
1. Navigate to `/contacts`
2. Click **New Contact**
3. Enter name `"Maria García"`, phone `"+52 55 1234 5678"`, email `"maria@example.com"`, click **Save**
4. **Expected:** Row appears with all three values; success toast shows

### Create a contact (name only)
5. Create a contact named `"Proveedor Anónimo"` with no phone or email
6. **Expected:** Row appears with name only; phone and email columns show empty; success toast shows

### Edit a contact
7. Click edit on `"Maria García"`, update email to `"mgarcía@example.com"`, click **Save**
8. **Expected:** Row reflects new email; success toast shows

### Invalid email rejected
9. Attempt to save a contact with email `"not-an-email"`
10. **Expected:** Inline validation error — invalid email format; form not submitted

### Delete a contact
11. Click delete on `"Proveedor Anónimo"`, confirm
12. **Expected:** Row removed; success toast shows

### Session persistence
13. Reload the page
14. **Expected:** All remaining contacts still present

---

## Navigation

1. While unauthenticated, navigate to `/categories` directly
2. **Expected:** Redirected to `/login`

3. While logged in, check sidebar (desktop) and bottom-nav (mobile viewport)
4. **Expected:** Both show **Categories** and **Contacts** nav items

---

## Edge Cases

- Create 10+ categories; verify the table renders all rows without layout breakage
- Use very long name (100+ chars); verify truncation or layout is acceptable
- Rapidly submit create form twice; verify only one record is created (no double-submit)

---

## Not Proven By This UAT

- Quarkus REST endpoints called directly (bypassing SvelteKit proxy) — covered by T01 compile verification
- Flyway migration order correctness with future V3+ migrations — covered when S03 is implemented
- Category/Contact cascading behavior when referenced by transactions or subscriptions — not yet implemented (S03, S05, S06)
