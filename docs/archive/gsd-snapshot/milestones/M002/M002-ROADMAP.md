# M002: UI Overhaul & Feature Completion

**Vision:** Polish and capability upgrade — dock navigation, mobile card layouts, system theme detection, category color badges, subscription model improvements (owner participation, manual billing, transaction linking), passkey auth via WorkOS, and M001 deferred fixes. The app should feel personal and complete.

## Success Criteria

- Dock nav renders correctly at desktop (all icons) and mobile (3 pinned + overflow menu)
- Mobile cards replace tables at ≤768px for transactions, subscriptions, debts with category badges
- Dark mode activates automatically with system preference, no flash on load
- Category badges show correct hues per direction in both themes
- Subscription billing splits correctly with/without owner participation
- Manual billing trigger generates expected payment records
- Transaction linking shows inline previews and persists FK association atomically
- Passkey-only login via WorkOS replaces password auth
- JUnit integration tests pass for new backend logic
- svelte-check + vite build clean
- Railway deploy succeeds with all M002 features functional

## Slices

- [x] **S01: S01** `risk:high` `depends:[]`
  > After this: Centered bottom dock renders on desktop (all icons) and mobile (3 pinned + overflow menu dialog); all navigation routes work in new layout

- [x] **S02: S02** `risk:medium` `depends:[]`
  > After this: App switches light/dark with system preference without flash; categories show colored badges with direction-constrained hues in both themes

- [x] **S03: S03** `risk:medium` `depends:[]`
  > After this: Owner participation toggle changes billing split math; manual trigger generates records on demand; transactions linkable to subscriptions with inline preview

- [x] **S04: S04** `risk:medium` `depends:[]`
  > After this: Transactions, subscriptions, and debts render as cards on mobile (≤768px) with category badges; tap navigates to detail view with action buttons

- [x] **S05: S05** `risk:high` `depends:[]`
  > After this: Password auth replaced entirely; passkey registration and login works end-to-end via WorkOS AuthKit

- [x] **S06: S06** `risk:low` `depends:[]`
  > After this: JUnit integration tests pass for new backend logic; layerchart Svelte 5 status resolved; Fraunces font renders correctly

- [x] **S07: S07** `risk:medium` `depends:[]`
  > After this: App deployed to Railway with all M002 features functional; manual billing trigger and theme detection verified in production

## Boundary Map

### S01 → S02

Produces:
- New app shell layout with dock component and `.dark` class toggle point on `<html>`
- Removed sidebar; all pages render within dock-based layout

Consumes:
- nothing (first slice for UI track)

### S01 → S04

Produces:
- App shell with dock navigation at both viewports
- Layout structure that card views will render within

Consumes:
- nothing (first slice for UI track)

### S02 → S04

Produces:
- Category color badge component rendering OKLCH hue with theme-adaptive lightness/chroma
- Dark/light theme switching via `.dark` class

Consumes:
- App shell from S01

### S02 → S06

Produces:
- Category `color` column via Flyway migration
- Theme infrastructure for visual verification

Consumes:
- App shell from S01

### S03 → S06

Produces:
- `owner_participates` column, manual billing endpoint, `subscription_id` FK on transactions
- Modified billing split logic in SubscriptionBillingScheduler

Consumes:
- nothing (independent backend track)

### S03 → S07

Produces:
- New API endpoints: POST /api/subscriptions/generate-billing, PUT /api/transactions/{id}/link-subscription
- Modified billing split math

Consumes:
- nothing (independent backend track)

### S05 → S07

Produces:
- WorkOS AuthKit integration replacing password auth
- Updated session management in SvelteKit hooks

Consumes:
- nothing (independent auth track)

### S06 → S07

Produces:
- JUnit test suite validating backend logic
- Resolved layerchart status and fixed Fraunces font

Consumes:
- Category color migration from S02, billing changes from S03
