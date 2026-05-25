---
estimated_steps: 28
estimated_files: 6
skills_used: []
---

# T04: Build responsive authenticated app shell with sidebar navigation and logout

Create the authenticated app shell layout visible after login. Desktop (≥640px): sidebar navigation with app name, nav links (Dashboard, Transactions, Subscriptions, Debts), and logout button at the bottom. Mobile (<640px): bottom tab bar with the same nav items as icons. The dashboard route (/) shows a placeholder card. Add logout action that clears the session cookie and redirects to /login. Update the root +layout.svelte to conditionally render the app shell when authenticated vs. bare layout for login.

---
estimated_steps: 7
estimated_files: 6
skills_used:
  - svelte-code-writer
  - svelte-core-bestpractices
---

## Steps

1. Create frontend/src/lib/components/app-shell/sidebar.svelte — desktop sidebar: fixed left, 240px wide, flex column with app branding at top ('Keenti Finances'), nav links with Lucide icons (LayoutDashboard, ArrowLeftRight, CreditCard, HandCoins), logout button at bottom. Uses active route highlighting via $page.url.pathname.
2. Create frontend/src/lib/components/app-shell/bottom-nav.svelte — mobile bottom tab bar: fixed bottom, full width, flex row with same 4 nav icons plus logout. Active tab highlighted.
3. Create frontend/src/lib/components/app-shell/app-shell.svelte — responsive wrapper: renders sidebar on sm+ screens (hidden on mobile), bottom-nav on <sm screens (hidden on desktop), main content area with proper padding/margins for each layout.
4. Update frontend/src/routes/+layout.svelte — check page data for session: if authenticated, render app-shell wrapping children; if not, render children directly (login page gets bare layout).
5. Update frontend/src/routes/+page.svelte — replace default content with dashboard placeholder: shadcn Card with title 'Dashboard' and body 'Coming soon — transaction charts and net balance will appear here.'
6. Create frontend/src/routes/logout/+page.server.ts — load function redirects to /login after clearing session cookie. Uses cookies.delete() and redirect(303, '/login').
7. Test responsive breakpoints: sidebar hidden below 640px, bottom-nav hidden at 640px+

## Must-Haves

- [ ] Desktop sidebar visible at sm+ (≥640px) with nav links and logout
- [ ] Mobile bottom nav visible below sm (<640px)
- [ ] Dashboard placeholder renders after login at /
- [ ] Logout clears session cookie and redirects to /login
- [ ] Layout is usable at 390px (iPhone) and 1440px (desktop)
- [ ] Active route highlighted in nav

## Verification

- cd frontend && bun run check exits 0
- test -f frontend/src/lib/components/app-shell/app-shell.svelte
- test -f frontend/src/routes/logout/+page.server.ts
- grep -q 'sm:' frontend/src/lib/components/app-shell/app-shell.svelte or sidebar.svelte

## Inputs

- `frontend/src/routes/+layout.svelte — layout to wrap with app shell`
- `frontend/src/routes/+layout.server.ts — session data from T03`
- `frontend/src/routes/+page.svelte — page to replace with dashboard placeholder`
- `frontend/src/hooks.server.ts — auth guard from T03`
- `frontend/src/lib/server/session.ts — session utilities from T03 for logout`

## Expected Output

- `frontend/src/lib/components/app-shell/sidebar.svelte — desktop sidebar navigation`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte — mobile bottom navigation`
- `frontend/src/lib/components/app-shell/app-shell.svelte — responsive app shell wrapper`
- `frontend/src/routes/+layout.svelte — updated with conditional app shell rendering`
- `frontend/src/routes/+page.svelte — dashboard placeholder page`
- `frontend/src/routes/logout/+page.server.ts — logout action clearing session`

## Verification

cd frontend && bun run check && echo 'TYPE CHECK OK' && test -f src/lib/components/app-shell/app-shell.svelte && test -f src/routes/logout/+page.server.ts && echo 'ALL CHECKS PASSED'
