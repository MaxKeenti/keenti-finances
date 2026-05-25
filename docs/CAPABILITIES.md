# Capabilities

What the product offers — and deliberately doesn't. This is a capability ledger, not a roadmap or a backlog. Add to it when the answer to "do we do X?" needs a stable home; don't use it to track in-flight tickets.

Seeded from `docs/archive/gsd-snapshot/REQUIREMENTS.md` (M001–M003 GSD register, captured 2026-05-25). Status reflects that snapshot — update as features ship or get cut.

**Status legend:** Shipped · In progress · Planned · Deferred · Out of scope.

---

## Money tracking

- **Transactions (INGRESS/EGRESS) with Category and Contact** — full CRUD, MXN-only. *Shipped (M001).*
- **Dashboard: net balance, monthly income vs. expenses, trend line** — SVG charts driven by native SQL aggregation. *Shipped (M001).*

## Categories

- **Categories with Direction (INGRESS / EGRESS / BOTH)** — full CRUD, per-User. *Shipped (M001 + M003).*
- **Category colour as OKLCH hue with theme-adaptive badges** — see ADR-0008. *Shipped (M002).*
- **Category hue is mandatory; stored as `SMALLINT` (0–359) with CHECK constraint** — see ADR-0017. *Planned (M003).*
- **Category colour picker: 360° hue wheel + hex input, live preview in both themes** — direction-constrained palette relaxed but new-Category default hue is still direction-seeded (INGRESS=100, EGRESS=10, BOTH=220). Hex input is lossy by design; the dual preview is the explanation. Vivid slider gradient (constant L=0.7, C=0.18) for hue navigation. *Planned (M003).*
- **System default categories seeded JIT at user creation** — 12 starter Categories written in `provisionUser()` so a new User lands on a populated Categories page on first login. *Planned (M003).*

## Subscriptions

- **Personal vs Shared Subscriptions with Members** — full CRUD. *Shipped (M001).*
- **Daily idempotent billing scheduler, 7-day lead time** — see ADR-0006. *Shipped (M001).*
- **Manual billing trigger** — POST endpoint + UI button; idempotent. *Shipped (M002).*
- **Owner Participation toggle (middleman mode)** — boolean on Subscription; see ADR-0009. *Shipped (M002).*
- **Public Subscription View** — unauthenticated, UUID-token-protected page where Members see payment status. Invalid tokens 404. *Shipped (M001).*
- **Transaction ↔ Subscription linking** — nullable FK, retroactive multi-select with inline previews; see ADR-0010. *Shipped (M002).*

## Debts

- **Debt with partial Debt Payments, auto-PAID when settled** — *Shipped (M001).*
- **Debt Payment auto-creates an INGRESS Transaction** — see ADR-0005. *Shipped (M001).*

## Multi-user & identity

- **WorkOS passkey-only auth** — `@workos-inc/node` with manual OAuth/PKCE in SvelteKit. See ADR-0004. *Shipped (M002).*
- **Per-User data isolation via Hibernate stacked filters (userScope + softDelete)** — see ADR-0011, ADR-0012, ADR-0014. *Shipped (M003).*
- **`X-WorkOS-User-Id` propagation + JIT user provisioning** — see ADR-0013. *Shipped (M003).*
- **`app_user.workos_id` (unique, indexed) as the bridge from WorkOS identity to local `user_id` FK** — also stores per-User preferences. *Shipped (M003).*
- **One-time data migration: existing rows assigned to User 1; `user_id` + `deleted_at` columns added to data tables; default Categories seeded** — *Shipped (M003).*
- **Categories are strictly per-User** — *Shipped (M003).*
- **Contacts are strictly per-User, never shared** — *Shipped (M003).*

## Soft delete

- **`deleted_at` on root entities; child entities inherit via parent** — see ADR-0014. Soft-deleted rows are excluded from all standard queries by Hibernate `@Filter`. *Shipped (M003).*
- **Partial unique index on `category.name` so soft-deleted Categories release their name slot** — see ADR-0015. *Shipped (M003).*
- **Soft-delete does not cascade across root-entity boundaries** — peer-root Transactions linked to a soft-deleted Debt or Subscription stay visible; see ADR-0016. *Shipped (M003).*
- **Trash view** — unified `/trash` page across all 5 root entity types with restore and permanent-delete. *Shipped (M003).*
- **CRUD completeness under multi-user + soft-delete** — soft-delete, restore, permanent-delete, list-deleted across every root entity. *Shipped (M003).*

## Personalization

- **System theme detection (`prefers-color-scheme`)** — no manual toggle, no flash on load. *Shipped (M002).*
- **Per-User theme customization** — primary OKLCH hue + heading/body font presets stored on `app_user`, applied via CSS variables loaded in `+layout.server.ts`. Primary hue rotates `--primary`, `--accent`, and `--primary-foreground` only; theme-fixed L/C, swap only the hue. Settings page auto-saves with debounce + "Saved ✓" indicator. *Planned (M003).*
- **Font preset list** — 3 sans (Geist, Inter, System UI) × 2 serif/display (Fraunces, Playfair Display). *Planned (M003).*
- **Font loading strategy** — the User's currently-selected fonts are preloaded via `<link rel="preload">` in `app.html` (server-rendered from preferences). Other presets lazy-import via CSS and incur a brief FOUT the first time they're picked in Settings. *Planned (M003).*

## Navigation & layout

- **Dock navigation** — centred bottom bar with static icons; desktop shows all items, mobile pins 3 (Transactions, Subscriptions, Debts) + overflow dialog. Sidebar removed. *Shipped (M002).*
- **Mobile card layouts** — card views (≤768px) replace tables for Transactions, Subscriptions, Debts; tap navigates to detail with action buttons. *Shipped (M002).*

## Quality, operations, infrastructure

- **Hexagonal backend (Quarkus)** — see ADR-0001. *Shipped (M001).*
- **SvelteKit-owns-auth, Quarkus-internal-only via SvelteKit proxy** — see ADR-0002, ADR-0003. *Shipped (M001).*
- **Railway two-service deploy with private networking** — see ADR-0007. *Shipped (M001 artifacts, M002 provisioning).*
- **`/q/health` probe + multi-stage Dockerfiles + `%prod` Quarkus profile** — *Shipped (M001).*
- **JUnit integration tests for daily billing scheduler under stacked filters** — scoped down from the broader M002 test backlog; covers idempotency, 7-day lead, and behaviour when a Subscription or its Owner User is soft-deleted. Owner-participation math and Transaction↔Subscription linking deliberately left untested — failures in either are immediately user-visible. *Planned (M003).*

---

## Deferred

- **PostgreSQL Row-Level Security** — DB-enforced row filtering as a second defence layer beyond Hibernate `@Filter`. Can layer on top of existing `user_id` columns without schema change. Defer until scale or compliance justifies the complexity.
- **Layerchart migration of the dashboard** — `layerchart@1.x` ships Svelte 5 native exports (verified 2026-05-25), so the prerequisite is met. Existing hand-rolled SVG charts (336 lines, driven by `d3-scale` directly) work; migration is deferred until we need a chart type we don't already have. *New charts should be built with layerchart from the outset.*
- **Owner-participation math and Transaction↔Subscription linking test suites** — both are simple enough that bugs surface immediately in the UI (wrong amounts on the Public Subscription View, mis-linked Transactions). Tests will be added when either area changes substantively.

## Out of scope (deliberate non-features)

- **Synthetic historical debt creation.** When linking historical Transactions to Subscriptions, do *not* auto-create Debt records for past billing periods. Retroactive tagging via the nullable FK is sufficient (see ADR-0010). Auto-creating Debts would duplicate data and create bookkeeping noise.
- **Microservices architecture.** The monolith with query-scoped security (ADR-0011) is intentional. Microservices add distributed-transaction overhead, operational cost, and inter-service auth burden without improving security for the expected user count.
- **Role-based permissions.** No roles, no admin/user hierarchy. Data ownership via `user_id` is the only authorization model. All Users are equal peers over their own data.
- **Multi-currency.** MXN only, throughout. No currency field on Transactions.
- **Manual theme toggle.** Theme follows `prefers-color-scheme` only (see "System theme detection" above).
- **First-login onboarding wizard.** Collapsed when system default Categories became JIT-seeded with no user-facing choice — a new User who logs in and immediately sees recognizable Categories needs no wizard. A guided UI tour was considered and rejected as friction.
- **Fraunces font fix (as a standalone capability).** No confirmed rendering bug; the M002 backlog item was precautionary. Verify in dev server when S04 ships; open a real issue if a bug is observed.
