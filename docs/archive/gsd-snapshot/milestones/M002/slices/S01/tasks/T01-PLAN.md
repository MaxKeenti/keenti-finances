---
estimated_steps: 14
estimated_files: 2
skills_used: []
---

# T01: Create Dock component replacing sidebar and bottom-nav

Why: The sidebar and scrolling bottom-nav need to be replaced with a single Dock component that renders a centered horizontal bar at the bottom of the viewport. Desktop shows all nav items as icons; mobile shows 3 pinned + overflow menu button.

Do:
1. Create `frontend/src/lib/components/app-shell/dock.svelte` with:
   - Centered horizontal bar fixed at bottom, full width, with glass-morphism background (backdrop-blur + semi-transparent bg)
   - Nav items as icon-only buttons with tooltip labels on hover (desktop)
   - On mobile (<768px): show only Transactions, Subscriptions, Debts icons + a Menu (EllipsisVertical) icon
   - Active route gets accent highlight (use $page.url.pathname matching)
   - Logout icon at far right separated by a divider
2. Create `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`:
   - Uses existing Dialog component from $lib/components/ui/dialog
   - Shows remaining nav items (Dashboard, Categories, Contacts) as a list with icons + labels
   - Triggered by the overflow menu button on mobile
3. Icons from @lucide/svelte: LayoutDashboard, ArrowLeftRight, CreditCard, HandCoins, Layers, Users, LogOut, EllipsisVertical

Done when: Both component files exist with correct Svelte 5 syntax ($props, $state, @render), using existing design tokens (sidebar-* CSS vars transitioning to new dock-* vars or reusing background/border vars).

## Inputs

- `frontend/src/lib/components/app-shell/sidebar.svelte`
- `frontend/src/lib/components/app-shell/bottom-nav.svelte`
- `frontend/src/lib/components/ui/dialog/index.ts`

## Expected Output

- `frontend/src/lib/components/app-shell/dock.svelte`
- `frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte`

## Verification

test -f frontend/src/lib/components/app-shell/dock.svelte && test -f frontend/src/lib/components/app-shell/dock-overflow-dialog.svelte
