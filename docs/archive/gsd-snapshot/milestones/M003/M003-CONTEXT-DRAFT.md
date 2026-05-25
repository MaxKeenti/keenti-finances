# M003: Multi-User Support, Soft Deletes & Personalization (DRAFT)

**Gathered:** 2026-05-21
**Status:** In discussion — architecture confirmed, error handling and quality bar pending

## Project Description

Evolve Keenti Finances from a single-user app into a multi-user system with data isolation, soft deletes across all entities, and user-level personalization (theme color, typography, category color picker).

## Why This Milestone

The app was built as single-user (R015 anti-feature). Directives changed — multi-user is now required. Soft deletes add data safety. Personalization makes the app feel owned by each user.

## Scope

### In Scope
- Multi-user data isolation: `user_id` FK on all data tables, repository-level enforcement via Hibernate @Filter
- Local `app_user` table linked to WorkOS ID — stores identity + preferences
- SvelteKit proxy injects `X-WorkOS-User-Id` header; Quarkus `UserContext` resolves local user
- Just-in-time user provisioning on first WorkOS login
- Data migration: existing rows assigned to current admin user
- Categories: system defaults seeded via Flyway + per-user customization; onboarding wizard for default selection
- Category color picker: full 360° hue wheel, hex input converting to OKLCH hue, live preview in both themes
- Contacts: strictly per-user
- Soft deletes: `deleted_at` timestamp across all entities, Hibernate @Filter default-filtering
- Fill remaining CRUD gaps under the new model
- User theme customization: primary OKLCH hue + typography presets (heading/body fonts)
- Public subscription view unchanged (token-scoped, stays public)

### Out of Scope
- Microservices architecture
- Role-based permissions beyond data ownership
- Multi-tenant schema isolation (shared schema with row-level filtering sufficient; PostgreSQL RLS can layer later)

## Architectural Decisions

### User identity propagation
**Decision:** Proxy adds `X-WorkOS-User-Id` header; Quarkus `@RequestScoped` `UserContext` bean reads it and resolves local `app_user.id`. First request auto-creates user row (JIT provisioning). New `workos_id` column (unique, indexed) on `app_user`.
**Rationale:** Keeps "SvelteKit owns auth" decision intact. Quarkus trusts header because only reachable via proxy.
**Alternatives:** Pass WorkOS JWT to Quarkus — adds JWT verification complexity to backend for no benefit since proxy is trusted.

### Soft delete strategy
**Decision:** Hibernate `@FilterDef` with `deleted_at IS NULL` on all entities, enabled per-session in repositories.
**Rationale:** Safety net — queries can't accidentally return soft-deleted rows. More robust than manual WHERE clauses.
**Alternatives:** Explicit `AND deleted_at IS NULL` in every query — simpler but one missed clause leaks deleted data.

### User-scoping strategy
**Decision:** Hibernate `@Filter` for `user_id = :currentUser`, stacked with soft-delete filter. Two filters active per request.
**Rationale:** Defense-in-depth — queries can't leak cross-user data even if someone writes a raw Panache find. Natural extension of the soft-delete filter approach.
**Alternatives:** Explicit WHERE clauses — same risk as soft-delete manual approach.

### User preferences storage
**Decision:** Theme preferences (primary color hue, heading font, body font) stored as columns directly on `app_user` table.
**Rationale:** Three varchar columns, loaded once per session. Avoids extra join/table for minimal settings.
**Alternatives:** Separate `user_preferences` table — unnecessary complexity for three fields.

### Category color picker upgrade
**Decision:** Widen palette to full 360° hue wheel with hex input that converts to nearest OKLCH hue. Live preview shows badge in both light/dark themes. Keep OKLCH storage for theme adaptation.
**Rationale:** Maximum personalization while preserving theme-adaptive rendering. Hex input is familiar UX; OKLCH conversion happens transparently.
**Alternatives:** Raw hex storage — loses theme adaptation, potential contrast issues in dark mode.

### Font loading strategy
**Decision:** Preload all preset fonts (4-6) in `app.html`, toggle via CSS variable per user preference.
**Rationale:** Small font set makes weight negligible. Instant switching with no FOUT.
**Alternatives:** Dynamic font loading — lighter initial load but FOUT on first visit.

### Data migration approach
**Decision:** Flyway migration adds `workos_id` to `app_user`, adds `user_id` FK + `deleted_at` to all data tables, assigns existing rows to user ID 1, seeds default system categories.
**Rationale:** Clean single-migration approach. Default categories available for onboarding wizard.

## Risks and Unknowns
- Hibernate @Filter + Panache compatibility — filters need explicit enabling per repository method; Panache static helpers may not respect them without configuration
- WorkOS user ID format stability — using as FK reference, need to confirm format won't change
- Migration on production data — needs careful testing with existing deployed data

## Existing Codebase
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/` — all Panache repositories need filter additions
- `frontend/src/routes/api/[...path]/+server.ts` — proxy needs X-WorkOS-User-Id injection
- `frontend/src/lib/server/auth.ts` — WorkOS session provides user identity
- `backend/src/main/resources/db/migration/` — V9 is latest migration
