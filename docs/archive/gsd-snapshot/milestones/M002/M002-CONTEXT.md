---
depends_on: [M001]
---

# M002: UI Overhaul & Feature Completion

**Gathered:** 2026-05-16
**Status:** Ready for planning

## Project Description

Polish and capability upgrade to the personal finance tracker — shifting the visual identity (macOS-style dock navigation, category color badges, system dark mode), making mobile actually useful for at-a-glance information (card layouts replacing tables), fixing subscription model gaps (owner participation toggle, manual billing trigger, retroactive transaction linking with inline previews), and catching up on M001 deferred work (passkeys via WorkOS replacing password auth, JUnit tests, Railway deployment, layerchart Svelte 5 fix, Fraunces font fix).

## Why This Milestone

M001 shipped a functional but scaffold-feeling app. The sidebar doesn't match the user's aesthetic, mobile tables are too dense for "information at a glance," subscriptions don't model reality (user is sometimes participant, sometimes middleman), there's no way to trigger billing on demand or link migrated historical data, and the deferred auth upgrade (passkeys) is the last major gap before the app feels personal and complete.

## User-Visible Outcome

### When this milestone is complete, the user can:

- Navigate the app via a centered bottom dock on both desktop and mobile
- View transactions, subscriptions, and debts as compact cards on mobile with category color badges
- Have the app automatically match their device's dark/light mode
- Toggle whether they're a participant or middleman in each shared subscription
- Trigger billing record generation on demand from the subscriptions page
- Link existing transactions to subscriptions with inline previews for easy identification
- Log in with a passkey instead of a password
- Use the app deployed on Railway with all features working in production

### Entry point / environment

- Entry point: Browser URL (Railway-hosted SvelteKit app)
- Environment: Production on Railway (two services + PostgreSQL)
- Live dependencies involved: WorkOS AuthKit (passkey auth), Railway PostgreSQL, Quarkus scheduled billing job

## Completion Class

- Contract complete means: svelte-check passes, vite build succeeds, JUnit integration tests pass for new backend logic, all endpoints return expected responses
- Integration complete means: SvelteKit proxy correctly forwards new API endpoints to Quarkus, WorkOS auth flow works end-to-end, billing trigger reuses scheduler logic
- Operational complete means: App deployed on Railway with new Flyway migrations applied, manual billing trigger works in production, theme detection functions behind HTTPS termination

## Final Integrated Acceptance

To call this milestone complete, we must prove:

- Dock navigation renders correctly at desktop and mobile viewports with overflow menu working
- Mobile card views show transactions/subscriptions/debts with category badges; tap navigates to detail with action buttons
- Dark mode activates automatically when device switches to dark mode, with no flash on load
- Subscription billing split correctly reflects owner participation toggle (participant vs middleman)
- Manual billing trigger generates expected payment records from subscriptions page
- Transaction linking with inline previews persists FK association atomically
- Passkey registration and login works as sole auth method via WorkOS
- All of the above verified on Railway production deployment

## Architectural Decisions

### Dock Navigation Pattern

**Decision:** Replace sidebar with centered horizontal bottom bar using static icons. Desktop shows all nav items. Mobile shows 3 pinned items (Transactions, Subscriptions, Debt) plus a menu button that opens an overflow dialog.

**Rationale:** macOS-style dock feels more personal and compact than a sidebar. Static icons (no magnification) keep implementation simple. Mobile pinning ensures the most-used items are always one tap away.

**Alternatives Considered:**
- Magnifying dock (macOS-style hover zoom) — Added complexity without clear benefit on a web app
- Sidebar with collapsible mobile drawer — Current pattern; user explicitly wants to move away from it

### System Theme Detection

**Decision:** Use `prefers-color-scheme` media query via an inline script in `app.html` that sets `.dark` class on `<html>` before first paint. No manual toggle — pure system-follow.

**Rationale:** Existing CSS already defines both light and dark OKLCH variable sets using `@custom-variant dark (&:is(.dark *))`. The inline script prevents flash of wrong theme. System-follow is sufficient for a single-user app used exclusively on Apple devices.

**Alternatives Considered:**
- Manual toggle with localStorage persistence — Unnecessary complexity for single-user system-follow
- CSS-only prefers-color-scheme without JS — Would require refactoring the existing .dark class-based approach

### Category Color Storage

**Decision:** Add `color` varchar column to category table storing an OKLCH hue value (e.g. "145" for green). Frontend renders badges with fixed lightness/chroma per theme (light mode: high lightness + medium chroma; dark mode: lower lightness + higher chroma). Curated swatch picker in create/edit form constrained by direction: green-ish for INGRESS, red-complement for EGRESS, blue-ish for BOTH.

**Rationale:** Storing just the hue keeps the palette consistent and theme-adaptive. Direction constraint preserves financial semantics (green=income, warm=expense, blue=mixed).

**Alternatives Considered:**
- Auto-assigned colors based on direction — Less personal; user wants to pick per category
- Full CSS color string storage — Over-specified; hue alone with fixed lightness/chroma is more maintainable

### Subscription Owner Participation

**Decision:** Add boolean `owner_participates` column (default true) to subscription table. Billing splits `cost / (memberCount + (ownerParticipates ? 1 : 0))`. When false (middleman mode), no payment record generated for owner.

**Rationale:** User is sometimes a participant in shared subscriptions (splits cost with members) and sometimes just the middleman (forwards full charge to members). A boolean flag cleanly captures both modes.

**Alternatives Considered:**
- Adding owner as a SubscriptionMember row — Would complicate the member management UI and break the owner/member distinction

### Manual Billing Trigger

**Decision:** `POST /api/subscriptions/generate-billing` endpoint reusing `generateUpcomingPaymentRecords()` from the scheduler. Button on subscriptions list page. Idempotent by design (scheduler already checks for existing records before creating).

**Rationale:** User needs up-to-date billing records without waiting for 1am cron. Reusing the scheduler method guarantees identical logic. Idempotency means no risk of duplicate records.

**Alternatives Considered:**
- Per-subscription trigger with fine control — Unnecessary complexity; global trigger is sufficient

### Transaction-Subscription Linking

**Decision:** Nullable `subscription_id` FK on transaction table. Subscription detail page shows "Link transactions" multi-select with inline previews (amount, date, description, category badge). Atomic linking — all selected or none. Debts not linkable separately (tracked by subscription structure).

**Rationale:** User is migrating historical data from other services. Retroactive tagging via FK is simpler and less error-prone than creating synthetic billing records. Inline previews prevent context-switching during linking.

**Alternatives Considered:**
- Synthetic historical debt creation — Would duplicate existing data; retroactive tagging is sufficient
- Debt linking in addition to transaction linking — Debts are already tracked by subscription; adding a second FK would be redundant

### Auth Migration to WorkOS

**Decision:** Replace password auth (D002/D004/D009) with WorkOS AuthKit using `@workos/authkit-sveltekit`. Passkey-only login. WorkOS manages sessions; SvelteKit validates WorkOS session instead of HMAC cookie.

**Rationale:** WorkOS free tier (1M MAU) handles the WebAuthn ceremony, session management, and passkey storage. Simpler than implementing WebAuthn from scratch. Passkeys are more secure and convenient for single-user app.

**Alternatives Considered:**
- Custom WebAuthn implementation — High complexity for credential storage, ceremony handling, and browser compatibility
- Passkey as second factor alongside password — More friction; passkey-only is cleaner for single user

### Mobile Card Layouts

**Decision:** Card-based list views at ≤768px breakpoint for transactions, subscriptions, and debts. Cards show essential info (amount, name/description, date, status/category badge). Tap navigates to detail view where action buttons live. Desktop table views unchanged.

**Rationale:** Tables don't compress well on narrow viewports. Cards preserve "information at a glance" promise. Detail view with actions keeps card surfaces clean and tappable.

**Alternatives Considered:**
- Responsive tables with horizontal scroll — User explicitly wants a rethink, not a workaround
- Summary cards on all viewports — Would lose the information density that tables provide on desktop

## Error Handling Strategy

- Manual billing trigger returns success with count of records generated, or a toast error if backend is unreachable
- Transaction linking is atomic — either all selected transactions get tagged or none do, with a clear error toast on failure
- Theme detection is purely CSS-driven with no failure path; inline script in app.html is synchronous
- Category color picker defaults to first swatch in the curated palette if no color is stored
- Mobile card views degrade gracefully to existing table layout if JS fails (progressive enhancement)
- WorkOS auth failures show WorkOS-provided error messaging; session expiry redirects to login

## Risks and Unknowns

- WorkOS integration replaces the entire auth layer (D002/D009) — session management shifts from HMAC cookie to WorkOS-managed sessions; all auth-dependent code paths change
- Dock navigation is a structural rearchitecture of the app shell — every page layout is affected by removing the sidebar
- Layerchart Svelte 5 compatibility is uncertain — may still not have native exports, making this a documentation-only outcome
- WorkOS passkeys are domain-bound — switching domains after registration breaks existing passkeys; must lock in production domain first

## Existing Codebase / Prior Art

- `frontend/src/routes/+layout.svelte` — Current app shell with sidebar; will be rearchitected for dock
- `frontend/src/lib/components/app-sidebar.svelte` — Sidebar component to be replaced
- `frontend/src/lib/components/mobile-bottom-nav.svelte` — Mobile nav to be replaced by dock
- `frontend/src/app.css` — OKLCH variable sets for light/dark themes already defined
- `backend/src/main/java/com/keenti/finances/application/service/SubscriptionBillingScheduler.java` — Scheduler logic to be reused for manual trigger
- `backend/src/main/java/com/keenti/finances/domain/model/Subscription.java` — Domain model to add ownerParticipates field
- `backend/src/main/java/com/keenti/finances/domain/model/Category.java` — Domain model to add color field
- `backend/src/main/java/com/keenti/finances/domain/model/Transaction.java` — Domain model to add subscriptionId FK
- `frontend/src/routes/login/+page.svelte` — Login page to be replaced with WorkOS auth flow
- `frontend/src/lib/server/auth.ts` — Auth helpers to be replaced with WorkOS session validation

## Relevant Requirements

- R002 — Dock navigation (S01)
- R003 — Mobile card layouts (S04)
- R004 — System theme detection (S02)
- R005 — Category color badges (S02)
- R006 — Subscription owner participation toggle (S03)
- R007 — Manual billing trigger (S03)
- R008 — Transaction-subscription linking (S03)
- R009 — Passkey auth via WorkOS (S05)
- R010 — JUnit integration tests (S06)
- R011 — Railway production deployment (S07)
- R012 — Layerchart Svelte 5 compatibility (S06)
- R013 — Fraunces font fix (S06)

## Scope

### In Scope

- macOS-style dock navigation replacing sidebar (desktop: all icons, mobile: 3 pinned + overflow)
- System theme detection via prefers-color-scheme (no manual toggle)
- Category color badges with direction-constrained curated palette
- Subscription owner_participates boolean toggle
- Manual billing trigger endpoint and UI button
- Transaction-subscription linking with inline previews
- Mobile card layouts for transactions/subscriptions/debts
- Passkey-only auth via WorkOS AuthKit
- JUnit integration tests for new backend logic
- Layerchart Svelte 5 compatibility check/migration
- Fraunces font fix
- Railway production deployment with smoke testing

### Out of Scope / Non-Goals

- Synthetic historical debt creation from subscription linking (R014)
- Multi-user authentication (R015)
- Manual theme toggle (system-follow only)
- Per-subscription billing trigger (global only)
- Debt-subscription linking (debts tracked by subscription structure)

## Technical Constraints

- Existing Flyway migration numbering continues from V6
- WorkOS free tier (1M MAU) — no cost constraint for single user
- Passkeys are domain-bound — production domain must be finalized before passkey registration
- SvelteKit `@custom-variant dark (&:is(.dark *))` pattern must be preserved for theme switching
- Category color hue stored as varchar to keep DB schema simple

## Integration Points

- WorkOS AuthKit — passkey registration, login, session management via @workos/authkit-sveltekit
- Railway — two services (backend, frontend) + PostgreSQL plugin; private networking between services
- Quarkus SubscriptionBillingScheduler — manual trigger reuses existing generateUpcomingPaymentRecords()
- SvelteKit catch-all proxy — new API endpoints (billing trigger, transaction linking) proxied to Quarkus

## Testing Requirements

- JUnit integration tests (Quarkus @QuarkusTest) for: billing split with/without owner participation, manual billing trigger idempotency, transaction-subscription FK linking
- `svelte-check` passes with 0 application errors (Effect library noise filtered)
- `vite build` exits 0
- Manual browser verification at desktop and mobile viewports for dock, cards, theme, badges
- Production smoke test on Railway: manual billing trigger, theme detection, passkey login

## Acceptance Criteria

- **S01 (Dock):** Centered bottom dock renders on desktop with all icons and on mobile with 3 pinned + working overflow menu dialog; all navigation routes work
- **S02 (Theme & Colors):** Dark mode activates with system preference, no flash; category badges render correct hues per direction in both themes
- **S03 (Subscriptions):** Owner participation toggle changes billing split math; manual trigger generates records; transaction linking shows inline previews and persists FK
- **S04 (Mobile Cards):** Cards replace tables at ≤768px for transactions/subscriptions/debts; category badges visible; tap navigates to detail with action buttons
- **S05 (Passkeys):** Passkey registration and login works via WorkOS; password auth removed; session persists
- **S06 (Deferred Fixes):** JUnit tests pass; layerchart status resolved; Fraunces font renders
- **S07 (Deployment):** Railway deploy succeeds with new migrations; billing trigger and theme verified in production

## Open Questions

- Layerchart Svelte 5 status — may ship native exports before implementation reaches S06, or may still be incompatible
- WorkOS domain configuration — Railway default domain or custom domain needs to be decided before passkey registration
