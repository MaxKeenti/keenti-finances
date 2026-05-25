# S04 Research: Mobile Card Layouts

## Summary of Findings

### Current State of Each Page

**Transactions (`/transactions/+page.svelte`)**
- Uses a `Table.Root` layout with columns: Date, Description, Amount, Category, Contact, Actions
- CategoryBadge already used in the Category column (with hue, name, direction props)
- Edit/Delete buttons inline in Actions column
- **No detail view exists** — no `/transactions/[id]` route
- Data loaded via `+page.server.ts` fetching from backend API (transactions, categories, contacts)

**Subscriptions (`/subscriptions/+page.svelte`)**
- **Already uses Card layout** (`Card.Root` in a responsive grid: `grid gap-4 sm:grid-cols-2 xl:grid-cols-3`)
- Cards show: name, cost, type badge, cycle badge, next billing date, member count
- Action buttons at bottom of each card (View, Edit, Members, Delete)
- Links to detail view at `/subscriptions/[id]` (exists)
- Does NOT use CategoryBadge

**Debts (`/debts/+page.svelte`)**
- **Already uses Card layout** (same grid pattern as subscriptions)
- Cards show: contact name, description, status badge, date, total/paid/remaining amounts
- Action buttons at bottom (Payments, Edit, Delete)
- Links to detail view at `/debts/[id]` (exists)
- Does NOT use CategoryBadge

### Key Observations

1. **Subscriptions and Debts already have card layouts** — they render identically on mobile and desktop (responsive grid). These may only need minor tweaks (if any) to satisfy the slice requirements.

2. **Transactions is the only page still using a table layout** — this is the primary work item for this slice.

3. **No transaction detail view exists** — the slice requirement says "Tap navigates to detail view where action buttons live." This means we need to create `/transactions/[id]/+page.svelte` with a detail view containing Edit/Delete actions.

4. **Progressive enhancement concern** — the requirement says "degrade to table if JS fails." The current transactions page already renders a table server-side, so showing the table by default and swapping to cards via a media query or Svelte conditional is the natural approach.

### Component Architecture

- **CategoryBadge** (`$lib/components/ui/category-badge/category-badge.svelte`): Takes `hue`, `name`, `direction` props. Uses `getIsDark()` from `$lib/theme.svelte` for OKLCH adaptive colors.
- **Card components** (`$lib/components/ui/card/`): Full shadcn-svelte card set (Card.Root, Card.Content, Card.Header, Card.Footer, etc.). Already used by subscriptions and debts pages.
- **Badge** (`$lib/components/ui/badge/`): Used for status/type indicators in subscriptions and debts.

### CSS/Tailwind Setup

- Tailwind CSS v4 with `@import 'tailwindcss'` (no tailwind.config — uses CSS-based config)
- Standard Tailwind breakpoints available (`sm:`, `md:`, `lg:`, `xl:`)
- Custom `@custom-variant dark` for dark mode
- OKLCH color system throughout
- No existing custom breakpoint utilities or media query helpers

### Data Available Per Entity

| Field | Transactions | Subscriptions | Debts |
|-------|-------------|---------------|-------|
| Primary label | description | name | contactName |
| Amount | amount (+ direction) | cost | totalAmount / remaining |
| Date | transactionDate | nextBillingDate | createdAt |
| Status/Badge | CategoryBadge | type + cycle badges | status badge |
| Secondary | contactName | member count | description |

## Implementation Landscape

### Files to Create
- `frontend/src/routes/transactions/[id]/+page.svelte` — transaction detail view
- `frontend/src/routes/transactions/[id]/+page.server.ts` — detail loader + actions

### Files to Modify
- `frontend/src/routes/transactions/+page.svelte` — add mobile card view alongside existing table (hide table on mobile, show cards)

### Files Potentially Unchanged
- `frontend/src/routes/subscriptions/+page.svelte` — already card-based; may need minor polish (ensure tap targets work, ensure link covers full card area)
- `frontend/src/routes/debts/+page.svelte` — same as subscriptions

### Patterns to Follow
- Grid layout pattern from subscriptions/debts: `grid gap-4 sm:grid-cols-2 xl:grid-cols-3`
- Card structure: `Card.Root > Card.Content` with flex layout
- Link wrapping for navigation: `<a href="/entity/{id}">` on the card title (already done in subs/debts)
- Badge usage for status indicators
- `fmt.format()` for currency (Intl.NumberFormat with es-MX/MXN)

## Natural Task Seams

### T01: Transaction Mobile Cards + Responsive Toggle
- Add mobile card view to `/transactions/+page.svelte`
- Show cards at `md:` breakpoint and below, table at `md:` and above
- Cards show: amount (colored), description, date, CategoryBadge, contact
- Card taps link to `/transactions/[id]`
- Progressive enhancement: table renders by default (SSR), cards enhance via CSS breakpoint

### T02: Transaction Detail View
- Create `/transactions/[id]/+page.server.ts` — load single transaction
- Create `/transactions/[id]/+page.svelte` — detail display with Edit/Delete actions
- Back navigation to `/transactions`

### T03: Polish Subscriptions & Debts Card Tap Targets
- Ensure subscription cards have full-card tap target linking to detail view (currently only title and "View" button link)
- Ensure debt cards have full-card tap target linking to detail view
- Verify mobile rendering is satisfactory at <=768px
- Minor: move Edit/Delete to detail views only on mobile (optional — may defer)

## Verification Approach

1. **Visual**: Resize browser to <=768px; confirm cards render for transactions, subscriptions, debts
2. **Navigation**: Tap a transaction card -> lands on detail view with correct data
3. **Progressive enhancement**: Disable JS -> transactions page shows table (SSR fallback)
4. **Desktop unchanged**: At >768px, transactions shows table layout as before
5. **CategoryBadge**: Visible on transaction cards with correct hue coloring
6. **Theme**: Cards render correctly in both light and dark modes

## Risks & Constraints

1. **No transaction detail route exists** — must create new route + server loader. Backend API must support single-transaction fetch (likely `GET /api/transactions/{id}` — needs verification).
2. **Table progressive enhancement** — need to ensure the table is still the SSR output and cards are shown via CSS media query (not JS-only conditional), so noscript users still get a functional table.
3. **Action button relocation** — moving Edit/Delete to detail view on mobile means users need an extra tap. This is per-spec ("Tap navigates to detail view where action buttons live") but may feel less efficient for power users.
4. **Subscriptions/Debts already card-based** — scope for T03 may be minimal (mostly verifying existing behavior satisfies requirements). Could be a very small task or skipped.
