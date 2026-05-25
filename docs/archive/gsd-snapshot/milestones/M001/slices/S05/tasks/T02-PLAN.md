---
estimated_steps: 24
estimated_files: 4
skills_used: []
---

# T02: Build SvelteKit /subscriptions CRUD page with member assignment

## Description

Create the /subscriptions page following the established SvelteKit CRUD pattern from S02 (categories/contacts). This page lets the user list all subscriptions, create new ones (personal or shared), edit them, delete them, and for shared subscriptions assign/remove members from the contacts pool.

## Steps

1. Create `frontend/src/routes/subscriptions/+page.server.ts`:
   - Load function: fetch /api/subscriptions, /api/categories, /api/contacts in parallel
   - Define subscriptionSchema with Zod: name (string, required), cost (number, positive), billingCycle (enum MONTHLY/YEARLY), type (enum PERSONAL/SHARED), categoryId (optional number), nextBillingDate (string date, required)
   - Actions: create (POST /api/subscriptions), update (PUT /api/subscriptions/{id}), delete (DELETE /api/subscriptions/{id}), addMember (POST /api/subscriptions/{id}/members with contactId), removeMember (DELETE /api/subscriptions/{id}/members/{memberId})
   - Follow the superforms create/update + plain enhance delete pattern from S02
2. Create `frontend/src/routes/subscriptions/+page.svelte`:
   - List subscriptions in cards showing name, cost formatted as MXN, billing cycle, type badge (Personal/Shared), member count for shared
   - Create/Edit dialog with form fields: name, cost, billing cycle select, type select, category select (optional), next billing date
   - When type is SHARED, show member management section: list current members with remove button, add member from contacts dropdown
   - Delete confirmation dialog
   - Toast notifications for all operations via sonner
3. Add 'Subscriptions' nav item to sidebar.svelte and bottom-nav.svelte (follow the pattern from S02 — icon from Lucide: CreditCard or Receipt)

## Must-Haves

- Single Zod schema for create and update (matching S02 pattern)
- Type toggle between PERSONAL/SHARED controls member section visibility
- Member add uses contacts dropdown; only shows contacts not already members
- Cost displayed formatted as MXN currency
- Responsive layout working at 390px and 1440px
- Toast notifications for success/error on all operations

## Verification

`bun run check` exits 0 AND `test -f frontend/src/routes/subscriptions/+page.svelte` AND `test -f frontend/src/routes/subscriptions/+page.server.ts` AND `grep -q 'subscriptions' frontend/src/lib/components/app-shell/sidebar.svelte` AND `grep -q 'subscriptions' frontend/src/lib/components/app-shell/bottom-nav.svelte`

## Inputs

- `frontend/src/routes/categories/+page.server.ts — reference for CRUD page.server pattern (superforms, actions)`
- `frontend/src/routes/categories/+page.svelte — reference for CRUD page pattern (dialogs, forms, toasts)`
- `frontend/src/routes/contacts/+page.server.ts — reference for optional field handling`
- `frontend/src/routes/transactions/+page.server.ts — reference for multi-entity load and select fields`
- `frontend/src/routes/transactions/+page.svelte — reference for select dropdowns and form layout`
- `frontend/src/lib/components/app-shell/sidebar.svelte — add Subscriptions nav item`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte — add Subscriptions nav item`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResource.java — API contract reference`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionRequest.java — request shape reference`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/SubscriptionResponse.java — response shape reference`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberRequest.java — member request shape`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/MemberResponse.java — member response shape`

## Expected Output

- `frontend/src/routes/subscriptions/+page.server.ts`
- `frontend/src/routes/subscriptions/+page.svelte`

## Verification

bun run check && test -f frontend/src/routes/subscriptions/+page.svelte && test -f frontend/src/routes/subscriptions/+page.server.ts && grep -q 'subscriptions' frontend/src/lib/components/app-shell/sidebar.svelte && grep -q 'subscriptions' frontend/src/lib/components/app-shell/bottom-nav.svelte
