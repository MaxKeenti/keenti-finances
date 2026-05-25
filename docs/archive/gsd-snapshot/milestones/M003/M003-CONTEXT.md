---
depends_on: [M002]
---

# M003: Multi-User, Soft Deletes & Personalization

**Gathered:** 2026-05-22
**Status:** Ready for planning

## Project Description

Evolve Keenti Finances from a single-user app to a multi-user system. Every data table gains a `user_id` FK enforced by Hibernate `@Filter` at the repository layer. Soft deletes (`deleted_at` timestamp) replace hard deletes across all entities, with a user-facing Trash view for recovery. The category color picker expands from curated direction-constrained swatches to a full 360° hue wheel with hex input (converting to OKLCH hue for theme-adaptive rendering). Each user gets per-user theme customization (primary color + heading/body font presets). New users land on an onboarding wizard to choose default categories.

## Why This Milestone

The app was designed as single-user (R015 anti-feature). Directives changed — multi-user is now required. The existing architecture (hexagonal with repository ports, SvelteKit proxy, WorkOS auth) has the right bones for this transition. The hardest work is retrofitting `user_id` onto every table and ensuring no query path leaks cross-user data. Soft deletes and personalization are natural companions since we're touching every entity anyway.

## User-Visible Outcome

### When this milestone is complete, the user can:

- Log in with their WorkOS passkey and see only their own transactions, categories, contacts, subscriptions, and debts — completely isolated from other users
- Delete any item and find it in a Trash view; restore it with one click
- Create/edit categories with a 360° color wheel or hex input, seeing a live badge preview in both light and dark themes
- Customize their app appearance (primary color, heading font, body font) in a Settings page
- Go through an onboarding wizard on first login to choose starting categories

### Entry point / environment

- Entry point: Browser at production URL (Railway) or localhost:5173 (dev)
- Environment: Railway production (Quarkus + SvelteKit + PostgreSQL) or local dev
- Live dependencies involved: WorkOS (identity/auth), PostgreSQL

## Completion Class

- Contract complete means: All Hibernate filters active, all queries scoped by user_id, soft-delete filtering on all standard queries, migration assigns existing data to user 1, default categories seeded
- Integration complete means: SvelteKit proxy injects X-WorkOS-User-Id, Quarkus resolves to local user, JIT provisioning works, theme preferences applied via CSS variables on page load
- Operational complete means: Two WorkOS users logged in simultaneously with fully isolated data, no cross-user data leakage on any endpoint

## Final Integrated Acceptance

To call this milestone complete, we must prove:

- Two distinct WorkOS users log in, create data, and see only their own data across all entity types
- User A deletes a transaction, sees it in Trash, restores it — user B's data unaffected throughout
- New user completes onboarding wizard, categories appear, app is usable immediately
- Category color picker renders hex input → OKLCH hue conversion with live preview in both themes
- User theme preferences (color + fonts) persist and apply on next login

## Architectural Decisions

### Monolith with query-scoped security over microservices

**Decision:** Keep the single Quarkus monolith; enforce multi-user security through Hibernate @Filter and UserContext injection at the repository layer.

**Rationale:** PostgreSQL MVCC handles concurrent users natively. 30 users generate trivial load. The hexagonal architecture already funnels all data access through repository ports — adding user-scope filtering at that layer gives tenant isolation without distributed transactions, inter-service auth, or operational overhead.

**Alternatives Considered:**
- Microservices — rejected because distributed transactions would complicate what's currently a single `@Transactional`, adds Railway cost and monitoring burden, doesn't improve security (same WHERE clause needed regardless)
- PostgreSQL Row Level Security — deferred (R029), can layer on top later without schema changes

### Hibernate @Filter for user-scoping and soft-delete

**Decision:** Use Hibernate `@FilterDef` with two stacked filters: `userScope` (user_id = :currentUser) and `softDelete` (deleted_at IS NULL), enabled per-session in each repository.

**Rationale:** Defense-in-depth — queries can't accidentally return cross-user or deleted rows. Same pattern for both concerns, natural to stack. More robust than manual WHERE clauses that can be forgotten.

**Alternatives Considered:**
- Explicit WHERE clauses in every Panache query — simpler and more visible, but one missed clause is a data leak
- JPA @Where annotation — deprecated in Hibernate 6, not recommended

### User identity propagation via header injection

**Decision:** SvelteKit proxy adds `X-WorkOS-User-Id` header to all forwarded requests. Quarkus `UserContext` @RequestScoped bean reads the header, resolves to local `app_user.id` via `workos_id` column. First request from unknown WorkOS user auto-creates the local record (JIT provisioning).

**Rationale:** Preserves "SvelteKit owns auth" (D002/D016). Quarkus trusts the header because it's only reachable via the proxy (private networking on Railway). Local `app_user` provides stable numeric FK and a home for preferences.

**Alternatives Considered:**
- Use WorkOS ID directly as FK — rejected because string FKs are slower, and we need a local record for preferences anyway
- JWT validation in Quarkus — unnecessary double-auth given the trusted proxy architecture

### Category color picker: hex input to OKLCH hue conversion

**Decision:** Expand from curated direction-constrained swatches to full 360° hue wheel + hex input. Hex values convert to nearest OKLCH hue. Badge rendering still uses fixed lightness/chroma per theme. Live preview shows badge in both light and dark modes.

**Rationale:** Maximum personalization while keeping theme-adaptive rendering. Users type a familiar hex value or pick from a wheel; the system converts to a hue that looks appropriate in both themes. Direction constraints (green=income, red=expense) are dropped in favor of user freedom.

**Alternatives Considered:**
- Full hex storage — rejected because fixed hex colors break in one theme mode (poor contrast)
- Keep direction-constrained palette — rejected by user in favor of full freedom

### Font preloading over dynamic loading

**Decision:** Preload all shadcn-svelte recommended font presets (~4-6 fonts) in `app.html`. Toggle active fonts via CSS variables per user preference.

**Rationale:** Small font set makes weight negligible. Preloading eliminates flash of unstyled text (FOUT) when user preferences load. Instant font switching without network requests.

**Alternatives Considered:**
- Dynamic `<link>` injection on page load — lighter initial load but visible FOUT on first visit

### User preferences on app_user table

**Decision:** Store primary_hue, heading_font, body_font as columns directly on `app_user` rather than a separate preferences table.

**Rationale:** Three columns don't warrant a separate table. Loaded once per session alongside user identity. Avoids an extra JOIN.

**Alternatives Considered:**
- Separate `user_preferences` table — unnecessary indirection for three fields

## Error Handling Strategy

- **Missing user identity:** If X-WorkOS-User-Id header is absent or doesn't resolve to a local user (and JIT provisioning fails), return 401. No silent fallback to unscoped queries.
- **Soft-deleted record access:** Direct URL to a soft-deleted entity returns 404 (not "this was deleted"). Same as if it never existed.
- **Cross-user access attempts:** Hibernate filter silently scopes queries; explicit ID-based lookup yielding nothing after filtering returns 404. Never reveal that a resource exists but belongs to someone else.
- **Category color input:** Validate hex format client-side; reject invalid formats server-side with 400. Hue conversion is deterministic.
- **Font preference mismatch:** If stored font doesn't match current preset list (e.g., font removed in future update), fall back to default font. No error surfaced.
- **Migration failure:** Flyway migrations are transactional; if user_id backfill fails, the whole migration rolls back.

## Risks and Unknowns

- **Missing WHERE clause is a data leak** — One query that forgets user-scope filtering exposes cross-user data. Mitigated by Hibernate @Filter (defense-in-depth), but requires careful review of every repository method.
- **Hibernate @Filter + Panache interplay** — Panache's static query helpers may not always respect Hibernate session-level filters. Need to verify behavior with `PanacheEntity.find()`, `PanacheEntity.listAll()`, and custom HQL queries.
- **Existing data migration complexity** — All existing rows need user_id backfill. FK constraints must be added after backfill, not before. Subscription members and debt payments have cascading ownership implications.
- **OKLCH hue conversion accuracy** — Converting arbitrary hex values to a single OKLCH hue dimension loses saturation/lightness information. Need to verify the perceptual quality of the conversion across the color space.

## Existing Codebase / Prior Art

- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/` — All Panache entity classes and repositories; every file here gets user_id + deleted_at + filter annotations
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/` — All REST resources; UserContext injection point
- `backend/src/main/java/com/keenti/finances/domain/model/` — Domain POJOs; gain userId field
- `backend/src/main/resources/db/migration/` — Flyway migrations V1-V9; new V10+ for multi-user + soft-delete
- `frontend/src/routes/api/[...path]/+server.ts` — Proxy catch-all; needs X-WorkOS-User-Id injection
- `frontend/src/lib/components/category-badge.svelte` — Current OKLCH hue badge rendering
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — Current user entity; gains workos_id, preference columns

## Relevant Requirements

- R015 — Multi-User Auth (REVERSED): now the primary driver for M003
- R016 — Multi-user data isolation: user_id FK + Hibernate @Filter
- R017 — User identity propagation: proxy header + UserContext bean
- R018 — Local user record: workos_id column + preferences
- R019 — Soft deletes: deleted_at + Hibernate @Filter
- R020 — Trash view: user-facing recovery UI
- R021 — Data migration: existing data to user 1 + defaults
- R022 — Category color picker upgrade: 360° hue + hex input
- R023 — User theme customization: primary color + fonts
- R024 — Font preloading: all presets in app.html
- R025 — Onboarding wizard: default category selection
- R026 — Categories per-user with defaults
- R027 — Contacts per-user
- R028 — CRUD completeness under new model

## Scope

### In Scope

- user_id FK on all data tables with Hibernate @Filter enforcement
- Local app_user linked to WorkOS ID with JIT provisioning
- SvelteKit proxy X-WorkOS-User-Id header injection
- Soft deletes (deleted_at) across all entities with Hibernate @Filter
- Trash view with restore capability
- Flyway migration: user_id + deleted_at columns, existing data to user 1, default categories
- Category color picker: 360° hue wheel + hex-to-OKLCH conversion + live preview
- Per-user theme: primary color + heading/body font presets via CSS variables
- Font preloading in app.html
- Onboarding wizard for new users (default category selection)
- Categories and contacts strictly per-user
- Fill remaining CRUD gaps

### Out of Scope / Non-Goals

- Microservices architecture (R030)
- Role-based permissions (R031)
- Multi-tenant schema isolation
- PostgreSQL Row Level Security (R029 — deferred)
- Direction-constrained category color palette (replaced by full 360° freedom)

## Technical Constraints

- Quarkus 3.35.2 with Hibernate ORM and Panache
- Flyway for schema migrations (must be backward-compatible with existing V1-V9)
- WorkOS provides user.id as a string (e.g., user_01H...); local app_user.id is bigserial
- SvelteKit proxy is the only path to Quarkus (private networking on Railway)
- OKLCH color model for theme-adaptive badge rendering
- shadcn-svelte font recommendations for typography presets

## Integration Points

- **WorkOS** — provides authenticated user identity (user.id, email, name) via session; SvelteKit reads session and injects into proxy headers
- **PostgreSQL** — all schema changes via Flyway; Hibernate @Filter for runtime query scoping
- **SvelteKit proxy** — gains header injection responsibility; all existing API routes continue working
- **Public subscription view** — token-scoped, stays public, not affected by user-scope filtering

## Testing Requirements

Manual testing with two WorkOS accounts verifying complete data separation across all entity types. Soft-delete and restore verified through Trash view. Category color picker verified visually in both themes. Theme preferences verified across login sessions.

## Acceptance Criteria

- Two WorkOS users logged in simultaneously see fully isolated data (no cross-user leakage on any entity type or endpoint)
- Deleting any entity moves it to Trash; Trash view shows all deleted items; restore returns item to normal view
- Category create/edit form shows 360° hue wheel + hex input with live badge preview in light and dark
- Settings page allows primary color + heading/body font selection; changes apply immediately and persist across sessions
- New WorkOS user sees onboarding wizard on first login; choosing defaults creates per-user categories; skipping leaves categories empty
- All existing data intact and assigned to original admin user after migration

## Open Questions

- Exact shadcn-svelte font preset list to include — resolve during S04 planning by checking current shadcn-svelte documentation
- Trash view UX: single unified trash page vs. per-entity trash tabs — resolve during S02 task planning
- Onboarding wizard: how many steps, what else besides category defaults — resolve during S05 task planning
