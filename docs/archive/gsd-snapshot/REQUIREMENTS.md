# Requirements

This file is the explicit capability and coverage contract for the project.

## Active

### R002 — Dock Navigation — centered horizontal bottom bar with static icons; desktop shows all nav items, mobile shows 3 pinned (Transactions, Subscriptions, Debt) plus overflow menu dialog
- Class: core-capability
- Status: active
- Description: Dock Navigation — centered horizontal bottom bar with static icons; desktop shows all nav items, mobile shows 3 pinned (Transactions, Subscriptions, Debt) plus overflow menu dialog
- Why it matters: Replace sidebar with a macOS-style dock that feels more personal and compact; mobile overflow keeps the most-used items always accessible
- Source: user
- Primary owning slice: M002/S01
- Supporting slices: M002/S04
- Validation: unmapped

### R003 — Mobile Card Layouts — card-based list views replacing tables on mobile (≤768px) for transactions, subscriptions, and debts; tap navigates to detail view with action buttons
- Class: primary-user-loop
- Status: active
- Description: Mobile Card Layouts — card-based list views replacing tables on mobile (≤768px) for transactions, subscriptions, and debts; tap navigates to detail view with action buttons
- Why it matters: Current tables are too wide for mobile; information-at-a-glance promise fails on small screens. Cards show essential info without horizontal scrolling.
- Source: user
- Primary owning slice: M002/S04
- Supporting slices: M002/S01, M002/S02
- Validation: unmapped

### R004 — System Theme Detection — app automatically switches between light and dark mode based on system prefers-color-scheme; no manual toggle; no flash on load
- Class: quality-attribute
- Status: active
- Description: System Theme Detection — app automatically switches between light and dark mode based on system prefers-color-scheme; no manual toggle; no flash on load
- Why it matters: User expects the app to respect device dark mode setting without manual intervention; existing OKLCH variable sets already define both palettes
- Source: user
- Primary owning slice: M002/S02
- Validation: unmapped

### R005 — Category Color Badges — user-assigned OKLCH hue per category from curated palette constrained by direction (green-ish INGRESS, red-complement EGRESS, blue-ish BOTH); rendered as badges wherever categories appear
- Class: core-capability
- Status: active
- Description: Category Color Badges — user-assigned OKLCH hue per category from curated palette constrained by direction (green-ish INGRESS, red-complement EGRESS, blue-ish BOTH); rendered as badges wherever categories appear
- Why it matters: Visual distinction between categories at a glance; direction-constrained palette keeps financial semantics clear (green=income, red-complement=expense)
- Source: user
- Primary owning slice: M002/S02
- Supporting slices: M002/S04
- Validation: unmapped

### R006 — Subscription Owner Participation Toggle — boolean owner_participates flag on subscription; when true cost splits among members + owner, when false splits among members only (middleman mode)
- Class: core-capability
- Status: active
- Description: Subscription Owner Participation Toggle — boolean owner_participates flag on subscription; when true cost splits among members + owner, when false splits among members only (middleman mode)
- Why it matters: User is sometimes a participant in shared subscriptions and sometimes just the middleman forwarding charges; the billing split must reflect both modes
- Source: user
- Primary owning slice: M002/S03
- Validation: unmapped

### R007 — Manual Billing Trigger — POST endpoint and subscriptions page button that runs generateUpcomingPaymentRecords() on demand; idempotent since scheduler already checks for existing records
- Class: core-capability
- Status: active
- Description: Manual Billing Trigger — POST endpoint and subscriptions page button that runs generateUpcomingPaymentRecords() on demand; idempotent since scheduler already checks for existing records
- Why it matters: No way to generate current billing records without waiting for 1am cron; user needs up-to-date payment information when managing subscriptions
- Source: user
- Primary owning slice: M002/S03
- Validation: unmapped

### R008 — Transaction-Subscription Linking — nullable subscription_id FK on transaction table; subscription detail page shows multi-select of unlinked transactions with inline previews (amount, date, description, category badge) for retroactive tagging
- Class: core-capability
- Status: active
- Description: Transaction-Subscription Linking — nullable subscription_id FK on transaction table; subscription detail page shows multi-select of unlinked transactions with inline previews (amount, date, description, category badge) for retroactive tagging
- Why it matters: User is migrating data from other services; historical transactions cannot be associated with subscriptions without a FK. Inline previews prevent page-switching during linking.
- Source: user
- Primary owning slice: M002/S03
- Validation: unmapped

### R009 — Passkey Auth via WorkOS — passwordless login using WorkOS AuthKit with @workos/authkit-sveltekit; passkey registration and login replaces password auth entirely
- Class: core-capability
- Status: active
- Description: Passkey Auth via WorkOS — passwordless login using WorkOS AuthKit with @workos/authkit-sveltekit; passkey registration and login replaces password auth entirely
- Why it matters: Deferred from M001 (D004); passkeys are more secure and convenient than passwords for a single-user app. WorkOS handles the WebAuthn ceremony.
- Source: user
- Primary owning slice: M002/S05
- Validation: unmapped

### R010 — JUnit Integration Tests — backend integration tests covering new endpoints (billing trigger, transaction linking, owner participation split) and modified billing logic
- Class: quality-attribute
- Status: active
- Description: JUnit Integration Tests — backend integration tests covering new endpoints (billing trigger, transaction linking, owner participation split) and modified billing logic
- Why it matters: Deferred from M001; backend has no test coverage. New billing split logic and transaction linking need automated verification.
- Source: user
- Primary owning slice: M002/S06
- Validation: unmapped

### R011 — Railway Production Deployment — app deployed to Railway with all M002 migrations; manual billing trigger and theme detection verified in production behind HTTPS termination
- Class: launchability
- Status: active
- Description: Railway Production Deployment — app deployed to Railway with all M002 migrations; manual billing trigger and theme detection verified in production behind HTTPS termination
- Why it matters: M001 produced deployment-ready artifacts but actual Railway provisioning was deferred; M002 features must work in production environment
- Source: user
- Primary owning slice: M002/S07
- Validation: unmapped

### R012 — Layerchart Svelte 5 Compatibility — verify whether layerchart has shipped Svelte 5 native exports; if yes, migrate dashboard charts from d3-scale direct to layerchart components; if no, document status and keep d3-scale path
- Class: continuity
- Status: active
- Description: Layerchart Svelte 5 Compatibility — verify whether layerchart has shipped Svelte 5 native exports; if yes, migrate dashboard charts from d3-scale direct to layerchart components; if no, document status and keep d3-scale path
- Why it matters: Known M001 gotcha — layerchart v1.0.13 exports Svelte 4 API causing type errors. Resolution needed for dashboard charting path clarity.
- Source: user
- Primary owning slice: M002/S06
- Validation: unmapped

### R013 — Fraunces Font Fix — fix @fontsource-variable/fraunces package so the font renders correctly in the app
- Class: continuity
- Status: active
- Description: Fraunces Font Fix — fix @fontsource-variable/fraunces package so the font renders correctly in the app
- Why it matters: Deferred from M001; the font package was installed but not working correctly
- Source: user
- Primary owning slice: M002/S06
- Validation: unmapped

### R015 — Multi-User Auth (REVERSED) — app now supports multiple users with isolated data via user_id FK on all tables, Hibernate @Filter enforcement, and WorkOS identity propagation; supersedes original R015 out-of-scope constraint
- Class: constraint
- Status: active
- Description: Multi-User Auth (REVERSED) — app now supports multiple users with isolated data via user_id FK on all tables, Hibernate @Filter enforcement, and WorkOS identity propagation; supersedes original R015 out-of-scope constraint
- Why it matters: Out of scope for M002; single-user model with contact-based subscription sharing is the established pattern
- Source: inferred
- Primary owning slice: M003/S01
- Supporting slices: M003/S02, M003/S05
- Validation: unmapped
- Notes: Directives changed — multi-user is now the primary goal for M003. Original R015 was an anti-feature constraint excluding multi-user.

### R016 — Multi-user data isolation — user_id FK on all data tables (transactions, categories, contacts, subscriptions, debts and children) with Hibernate @Filter enforcement at repository layer
- Class: core-capability
- Status: active
- Description: Multi-user data isolation — user_id FK on all data tables (transactions, categories, contacts, subscriptions, debts and children) with Hibernate @Filter enforcement at repository layer
- Why it matters: Core security invariant: user A must never see user B's data. Row-level filtering via stacked Hibernate filters provides defense-in-depth without microservices complexity.
- Source: user
- Primary owning slice: M003/S01
- Supporting slices: M003/S02
- Validation: unmapped
- Notes: Hibernate @Filter for user_id = :currentUser stacked with soft-delete filter on every entity

### R017 — User identity propagation — SvelteKit proxy injects X-WorkOS-User-Id header, Quarkus UserContext @RequestScoped bean resolves local app_user.id, JIT provisioning on first login
- Class: core-capability
- Status: active
- Description: User identity propagation — SvelteKit proxy injects X-WorkOS-User-Id header, Quarkus UserContext @RequestScoped bean resolves local app_user.id, JIT provisioning on first login
- Why it matters: Bridges the gap between WorkOS identity and local data ownership. SvelteKit owns auth; Quarkus trusts the header because it's only reachable via proxy.
- Source: collaborative
- Primary owning slice: M003/S01
- Validation: unmapped

### R018 — Local user record linked to WorkOS ID — app_user gains workos_id column (unique, indexed), stores user identity and preferences (theme color, typography)
- Class: core-capability
- Status: active
- Description: Local user record linked to WorkOS ID — app_user gains workos_id column (unique, indexed), stores user identity and preferences (theme color, typography)
- Why it matters: Provides stable numeric FK for all data tables and a home for per-user preferences. Decouples internal identity from WorkOS string IDs.
- Source: collaborative
- Primary owning slice: M003/S01
- Supporting slices: M003/S04
- Validation: unmapped

### R019 — Soft deletes across all entities — deleted_at timestamp column, Hibernate @Filter defaulting to deleted_at IS NULL, soft-deleted rows excluded from all standard queries
- Class: core-capability
- Status: active
- Description: Soft deletes across all entities — deleted_at timestamp column, Hibernate @Filter defaulting to deleted_at IS NULL, soft-deleted rows excluded from all standard queries
- Why it matters: Data safety and auditability. Users can recover accidentally deleted records. Backend never permanently loses data.
- Source: user
- Primary owning slice: M003/S02
- Validation: unmapped

### R020 — Trash view — user-facing page showing soft-deleted items across all entity types with restore capability
- Class: primary-user-loop
- Status: active
- Description: Trash view — user-facing page showing soft-deleted items across all entity types with restore capability
- Why it matters: Soft deletes without a trash view means users can't self-serve recovery. Visible trash builds confidence that deletion is reversible.
- Source: user
- Primary owning slice: M003/S02
- Validation: unmapped

### R021 — Data migration — existing rows assigned to user 1, user_id + deleted_at columns added to all data tables, default categories seeded via Flyway
- Class: continuity
- Status: active
- Description: Data migration — existing rows assigned to user 1, user_id + deleted_at columns added to all data tables, default categories seeded via Flyway
- Why it matters: Ensures zero data loss during multi-user transition. Existing admin data remains intact and accessible under the new model.
- Source: collaborative
- Primary owning slice: M003/S01
- Validation: unmapped

### R022 — Category color picker upgrade — 360 degree hue wheel plus hex input converting to OKLCH hue, live badge preview in both light and dark themes, inline in category create and edit form
- Class: core-capability
- Status: active
- Description: Category color picker upgrade — 360 degree hue wheel plus hex input converting to OKLCH hue, live badge preview in both light and dark themes, inline in category create and edit form
- Why it matters: Expands personalization beyond curated direction-constrained swatches while preserving theme-adaptive rendering. Hex input is familiar; OKLCH conversion ensures badges look good in both themes.
- Source: user
- Primary owning slice: M003/S03
- Validation: unmapped

### R023 — User theme customization — primary color (OKLCH hue) plus heading and body font presets stored on app_user, applied via CSS variables on page load
- Class: differentiator
- Status: active
- Description: User theme customization — primary color (OKLCH hue) plus heading and body font presets stored on app_user, applied via CSS variables on page load
- Why it matters: Makes the app feel personal per user. Each user's interface reflects their aesthetic preference without affecting other users.
- Source: user
- Primary owning slice: M003/S04
- Validation: unmapped

### R024 — Font preloading — all shadcn-svelte recommended font presets preloaded in app.html, toggled via CSS variable per user preference
- Class: quality-attribute
- Status: active
- Description: Font preloading — all shadcn-svelte recommended font presets preloaded in app.html, toggled via CSS variable per user preference
- Why it matters: Eliminates flash of unstyled text when switching fonts. Small font set makes preloading negligible in weight.
- Source: collaborative
- Primary owning slice: M003/S04
- Validation: unmapped

### R025 — Onboarding wizard — new users choose default category set or skip on first login, categories created per-user from system defaults
- Class: primary-user-loop
- Status: active
- Description: Onboarding wizard — new users choose default category set or skip on first login, categories created per-user from system defaults
- Why it matters: New users need a starting point. Choosing defaults during onboarding avoids an empty-state problem and lets users customize from the start.
- Source: user
- Primary owning slice: M003/S05
- Validation: unmapped

### R026 — Categories per-user with system defaults — each user gets their own categories, optionally seeded from system defaults during onboarding
- Class: core-capability
- Status: active
- Description: Categories per-user with system defaults — each user gets their own categories, optionally seeded from system defaults during onboarding
- Why it matters: Multi-user requires per-user categories. System defaults provide a useful starting set without forcing manual creation.
- Source: collaborative
- Primary owning slice: M003/S01
- Supporting slices: M003/S05
- Validation: unmapped

### R027 — Contacts per-user — strictly user-scoped with no sharing between users
- Class: core-capability
- Status: active
- Description: Contacts per-user — strictly user-scoped with no sharing between users
- Why it matters: Contacts contain personal financial relationships. No user should see another user's contact list.
- Source: collaborative
- Primary owning slice: M003/S01
- Validation: unmapped

### R028 — CRUD completeness — fill remaining create, update, delete gaps under the multi-user plus soft-delete model across all entity types
- Class: continuity
- Status: active
- Description: CRUD completeness — fill remaining create, update, delete gaps under the multi-user plus soft-delete model across all entity types
- Why it matters: Multi-user and soft-delete changes touch every CRUD path. Ensuring completeness prevents broken flows for any entity.
- Source: collaborative
- Primary owning slice: M003/S02
- Validation: unmapped

## Validated

### R001 — Untitled
- Status: validated
- Validation: Backend POST /api/auth/login with bcrypt verification compiles and links cleanly. SvelteKit hooks.server.ts auth guard redirects unauthenticated requests to /login. Session managed with HMAC-SHA256 signed HTTP-only cookies via timingSafeEqual validation. bun run check passes with 0 errors. All S01 task verification evidence passes.

## Deferred

### R029 — PostgreSQL Row Level Security — database-enforced row filtering as additional defense layer beyond Hibernate @Filter
- Class: compliance/security
- Status: deferred
- Description: PostgreSQL Row Level Security — database-enforced row filtering as additional defense layer beyond Hibernate @Filter
- Why it matters: Defense-in-depth. If application-level filtering has a bug, RLS prevents data leaks at the database level. Not needed at current scale but valuable as user count grows.
- Source: inferred
- Notes: Can layer on top of existing user_id columns without schema changes. Defer until scale or compliance needs justify the complexity.

## Out of Scope

### R014 — Synthetic Historical Debt Creation — do not auto-create debt records for past subscription billing periods when linking historical transactions
- Class: anti-feature
- Status: out-of-scope
- Description: Synthetic Historical Debt Creation — do not auto-create debt records for past subscription billing periods when linking historical transactions
- Why it matters: Retroactive tagging is sufficient; creating synthetic debt structures would duplicate existing data and create bookkeeping noise
- Source: inferred
- Validation: n/a

### R030 — Microservices architecture — monolith with query-scoped security; no service decomposition
- Class: constraint
- Status: out-of-scope
- Description: Microservices architecture — monolith with query-scoped security; no service decomposition
- Why it matters: Prevents scope confusion. Discussed and rejected: microservices add distributed transaction overhead, operational complexity, and cost without improving security for 30 users.
- Source: user

### R031 — Role-based permissions — no roles beyond data ownership; all users are equal peers
- Class: constraint
- Status: out-of-scope
- Description: Role-based permissions — no roles beyond data ownership; all users are equal peers
- Why it matters: Prevents scope creep into admin/user role hierarchies. Data ownership via user_id is sufficient for the current user model.
- Source: inferred

## Traceability

| ID | Class | Status | Primary owner | Supporting | Proof |
|---|---|---|---|---|---|
| R001 |  | validated | none | none | Backend POST /api/auth/login with bcrypt verification compiles and links cleanly. SvelteKit hooks.server.ts auth guard redirects unauthenticated requests to /login. Session managed with HMAC-SHA256 signed HTTP-only cookies via timingSafeEqual validation. bun run check passes with 0 errors. All S01 task verification evidence passes. |
| R002 | core-capability | active | M002/S01 | M002/S04 | unmapped |
| R003 | primary-user-loop | active | M002/S04 | M002/S01, M002/S02 | unmapped |
| R004 | quality-attribute | active | M002/S02 | none | unmapped |
| R005 | core-capability | active | M002/S02 | M002/S04 | unmapped |
| R006 | core-capability | active | M002/S03 | none | unmapped |
| R007 | core-capability | active | M002/S03 | none | unmapped |
| R008 | core-capability | active | M002/S03 | none | unmapped |
| R009 | core-capability | active | M002/S05 | none | unmapped |
| R010 | quality-attribute | active | M002/S06 | none | unmapped |
| R011 | launchability | active | M002/S07 | none | unmapped |
| R012 | continuity | active | M002/S06 | none | unmapped |
| R013 | continuity | active | M002/S06 | none | unmapped |
| R014 | anti-feature | out-of-scope | none | none | n/a |
| R015 | constraint | active | M003/S01 | M003/S02, M003/S05 | unmapped |
| R016 | core-capability | active | M003/S01 | M003/S02 | unmapped |
| R017 | core-capability | active | M003/S01 | none | unmapped |
| R018 | core-capability | active | M003/S01 | M003/S04 | unmapped |
| R019 | core-capability | active | M003/S02 | none | unmapped |
| R020 | primary-user-loop | active | M003/S02 | none | unmapped |
| R021 | continuity | active | M003/S01 | none | unmapped |
| R022 | core-capability | active | M003/S03 | none | unmapped |
| R023 | differentiator | active | M003/S04 | none | unmapped |
| R024 | quality-attribute | active | M003/S04 | none | unmapped |
| R025 | primary-user-loop | active | M003/S05 | none | unmapped |
| R026 | core-capability | active | M003/S01 | M003/S05 | unmapped |
| R027 | core-capability | active | M003/S01 | none | unmapped |
| R028 | continuity | active | M003/S02 | none | unmapped |
| R029 | compliance/security | deferred | none | none | unmapped |
| R030 | constraint | out-of-scope | none | none | unmapped |
| R031 | constraint | out-of-scope | none | none | unmapped |

## Coverage Summary

- Active requirements: 26
- Mapped to slices: 26
- Validated: 1 (R001)
- Unmapped active requirements: 0
