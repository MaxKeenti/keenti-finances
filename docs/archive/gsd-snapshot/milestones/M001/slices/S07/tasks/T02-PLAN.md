---
estimated_steps: 28
estimated_files: 3
skills_used: []
---

# T02: Build SvelteKit /public/subscription/[token] read-only page and add auth bypass

## Description

Create a public SvelteKit route at `/public/subscription/[token]` that fetches subscription data from the new public backend endpoint and renders a read-only view showing subscription name, cost, billing cycle, and a table of members with their payment status per billing period. Add `/public` to the PUBLIC_PATHS in `hooks.server.ts` so this route bypasses auth. Invalid tokens show a 404 page.

## Steps

1. Add `'/public'` to the `PUBLIC_PATHS` array in `frontend/src/hooks.server.ts`
2. Create `frontend/src/routes/public/subscription/[token]/+page.server.ts`:
   - Load function calls `GET http://localhost:8080/api/public/subscriptions/${params.token}`
   - On 404 response, throw SvelteKit `error(404, 'Subscription not found')`
   - On success, return the composite response data
3. Create `frontend/src/routes/public/subscription/[token]/+page.svelte`:
   - Display subscription name, cost, billing cycle, next billing date in a header card
   - Show a table/card layout of members with columns: Member Name, Billing Date, Amount, Status (PENDING/PAID badge), Paid Date
   - Group payments by billing date (newest first), show each member's status within that period
   - Use consistent styling with the rest of the app (Tailwind classes)
   - No edit/delete/action buttons — purely read-only
   - Show a clear message if there are no members or no payment records yet
4. Create `frontend/src/routes/public/subscription/[token]/+layout.svelte` (optional) to provide a minimal layout without sidebar/nav since this is a public page

## Must-Haves

- [ ] `/public` added to PUBLIC_PATHS in hooks.server.ts
- [ ] +page.server.ts fetches from public backend endpoint
- [ ] +page.svelte renders subscription info + member payment status
- [ ] Invalid tokens show 404
- [ ] No action buttons or mutation forms — read-only
- [ ] Page works without authentication

## Verification

- `grep -q "'/public'" frontend/src/hooks.server.ts`
- `test -f frontend/src/routes/public/subscription/\[token\]/+page.server.ts`
- `test -f frontend/src/routes/public/subscription/\[token\]/+page.svelte`
- `cd frontend && bun run check 2>&1 | grep -v 'node_modules' | grep -i 'error' | grep -v 'SubscriptionRef' | wc -l` returns 0

## Inputs

- `frontend/src/hooks.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.server.ts`
- `frontend/src/routes/subscriptions/[id]/+page.svelte`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PublicSubscriptionResource.java`

## Expected Output

- `frontend/src/routes/public/subscription/[token]/+page.server.ts`
- `frontend/src/routes/public/subscription/[token]/+page.svelte`

## Verification

grep -q '/public' frontend/src/hooks.server.ts && test -f frontend/src/routes/public/subscription/\[token\]/+page.server.ts && test -f frontend/src/routes/public/subscription/\[token\]/+page.svelte
