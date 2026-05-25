# M001: Personal Finance Tracker

**Gathered:** 2026-05-12
**Status:** Ready for planning

## Project Description

A single-user personal finance app that tracks ingresses (income) and egresses (expenses) in MXN, provides a dashboard with monthly/yearly views showing net balance and trends, manages personal and shared subscriptions with auto-generated billing records, tracks debts with partial payment support, and offers a public read-only view for subscription members. Deployed on Railway with Quarkus + SvelteKit + PostgreSQL.

## Why This Milestone

The user has no finance tracking system for 2026. Card history exists but nothing is being recorded or visualized. Shared subscription payments and embroidery side hustle debts are tracked mentally or informally. This milestone delivers the complete working product — there's no value in a partial deploy.

## User-Visible Outcome

### When this milestone is complete, the user can:

- Log in on mobile Safari, see a dashboard with real income vs. expenses charts and net balance
- Add, edit, and delete transactions categorized by user-defined categories
- Create personal and shared subscriptions, see auto-generated upcoming payment records
- Share a token link with subscription members so they can see their payment status
- Record embroidery jobs as debts, accept partial payments that auto-register as income
- Access the full app on desktop and mobile Safari, deployed on Railway with HTTPS

### Entry point / environment

- Entry point: HTTPS URL on Railway (custom domain or Railway subdomain)
- Environment: Production on Railway — PostgreSQL database, Quarkus backend, SvelteKit frontend
- Live dependencies involved: PostgreSQL (Railway-provisioned), Quarkus scheduler (in-process)

## Completion Class

- Contract complete means: All API endpoints return correct data, forms validate on both layers, scheduled job generates payment records, debt payments create ingress transactions
- Integration complete means: SvelteKit proxy correctly forwards all requests to Quarkus, auth session flows end-to-end, public token links resolve to correct subscription data
- Operational complete means: App is deployed on Railway, HTTPS works, PostgreSQL is provisioned, scheduler runs on its cron

## Final Integrated Acceptance

To call this milestone complete, we must prove:

- Full login → dashboard → add transaction → see it reflected in charts flow on mobile Safari
- Create shared subscription → scheduler generates payment records → public link shows correct member status
- Record debt → partial payment → ingress auto-created → dashboard balance updated
- All of the above on the Railway deployment, not just local dev

## Architectural Decisions

### Hexagonal Architecture on Quarkus

**Decision:** Use hexagonal (ports & adapters) architecture for the Quarkus backend.

**Rationale:** Keeps domain logic framework-independent, testable through ports. Domain models are clean POJOs, Panache entities live in infrastructure as adapters.

**Alternatives Considered:**
- Flat Panache structure — simpler but couples domain to Hibernate annotations
- CQRS — overkill for a single-user app

### SvelteKit Owns the Session

**Decision:** SvelteKit manages authentication sessions via signed HTTP-only cookies. Quarkus is a trusted internal API with no session management.

**Rationale:** Single auth layer, no CORS complexity, cookie never touches browser JS. Quarkus stays a pure API without auth concerns.

**Alternatives Considered:**
- Quarkus manages sessions — adds CORS, cookie forwarding complexity through the proxy
- JWT in localStorage — XSS risk, unnecessary for server-rendered proxy setup

### Layerchart for Dashboard Visualizations

**Decision:** Use Layerchart (via shadcn-svelte's Chart component) for income vs. expenses and trend charts.

**Rationale:** Svelte-native, built on D3, integrates with Tailwind and shadcn's design language. Already bundled with shadcn-svelte.

**Alternatives Considered:**
- Chart.js via svelte-chartjs — simpler but less Svelte-idiomatic, doesn't match shadcn aesthetics

### Simple Password Auth (Passkeys Deferred)

**Decision:** Use hashed password authentication for M001. Passkey/WebAuthn deferred to M002.

**Rationale:** Reduces M001 complexity. Single user with a strong password behind HTTPS is sufficient. WebAuthn adds client-side ceremony and server-side credential storage that can wait.

**Alternatives Considered:**
- Passkeys in M001 — adds complexity without blocking value delivery

### Debt Payments Auto-Create Ingress Transactions

**Decision:** When a partial or full debt payment is recorded, the system automatically creates a corresponding ingress transaction.

**Rationale:** Keeps the dashboard's income figures accurate without manual double-entry. The user records the payment once; the system handles the accounting.

**Alternatives Considered:**
- Manual ingress creation — error-prone, defeats the purpose of automation

### Subscription Scheduler with 7-Day Lead

**Decision:** A Quarkus `@Scheduled` job runs daily, generating pending payment records for subscriptions whose next billing date is within 7 days.

**Rationale:** Members see "upcoming" on the public view, and unpaid periods accumulate visibly. 7 days gives reasonable advance notice.

**Alternatives Considered:**
- Manual period creation — defeats the subscription tracking purpose
- Generate at billing date only — no advance visibility for members

## Error Handling Strategy

- SvelteKit shows toast notifications (sonner) for API errors with user-friendly messages
- Quarkus returns structured JSON errors with HTTP status codes; SvelteKit translates them for display
- Form validation via Zod on frontend, bean validation on backend — reject bad data at both boundaries
- Scheduled job failures log and retry on next run — no silent swallowing
- Public link returns 404 for invalid tokens (no information leakage)
- Optimistic UI for quick actions (marking payments) with rollback on failure
- Session expiry redirects to login page

## Risks and Unknowns

- **Quarkus WebAuthn maturity** — deferred to M002, not a risk for this milestone
- **Layerchart API for specific chart types** — may need to drop to raw D3 if Layerchart doesn't support a needed chart; mitigated by shadcn-svelte's built-in Chart wrapper
- **Railway deployment config** — user has deployed this stack before; low risk but needs verification
- **Scheduled job reliability** — `@Scheduled` runs in-process; if the app restarts mid-cycle, the next run catches up since it checks billing dates, not time since last run

## Existing Codebase / Prior Art

- `backend/pom.xml` — Quarkus 3.35.2 with REST, Flyway, PostgreSQL JDBC. Needs Panache, Jackson, Scheduler additions.
- `backend/src/main/java/org/acme/GreetingResource.java` — Default greeting endpoint, will be replaced
- `backend/src/main/resources/application.properties` — Needs DB config, scheduler config
- `frontend/package.json` — SvelteKit with shadcn-svelte, superforms, formsnap, Tailwind 4, sonner, Lucide
- `frontend/src/lib/components/ui/` — shadcn components already scaffolded (button, card, dialog, form, input, calendar, etc.)

## Relevant Requirements

- R001–R015 — All active requirements are owned by M001 slices
- R016 (Passkeys) — Deferred to M002
- R017 (Uneven splits) — Deferred to future
- R021 (Per-member scoped public view) — Deferred to M002

## Scope

### In Scope

- Password-based single-user authentication with session management
- User-defined transaction categories (full CRUD)
- Transaction tracking: ingresses and egresses with amount (MXN), date, description, category
- Financial dashboard: monthly/yearly toggle, net balance, income vs. expenses charts, trend visualization
- Reusable contacts pool for subscription members and debtors
- Personal subscription management (name, cost, billing cycle)
- Shared subscription management with equal splits and manual payment recording
- Auto-generated subscription payment records with 7-day billing lead time
- Debt tracking: individual invoices per debtor, partial payments, auto-created ingress transactions
- Public read-only subscription view via UUID token link
- Responsive layout for desktop and mobile Safari
- Railway deployment with HTTPS

### Out of Scope / Non-Goals

- Multi-user registration or user management
- CSV/Excel import (backfill via direct SQL on Railway)
- Uneven subscription splits
- Non-Safari browser optimization
- External payment integrations (SPEI, bank APIs)
- Multi-currency support
- Passkeys/WebAuthn (M002)

## Technical Constraints

- Apple devices + Safari only — no need for cross-browser testing
- MXN single currency — no exchange rates or currency formatting variants
- Hexagonal architecture on backend — domain must be framework-free
- SvelteKit proxies all API calls — Quarkus is never exposed to the browser

## Integration Points

- **PostgreSQL (Railway)** — Primary data store, provisioned on Railway
- **Quarkus Scheduler** — In-process `@Scheduled` job for subscription billing record generation
- **SvelteKit ↔ Quarkus** — Server-side proxy via SvelteKit `+server.ts` routes

## Testing Requirements

- Backend: JUnit integration tests against test PostgreSQL (Flyway-managed), testing use cases through hexagonal ports
- Frontend: Manual verification through the running app in Safari
- No unit tests on thin wrappers (Panache adapters, SvelteKit load functions)
- Each slice verified through the proxy — no stub endpoints

## Acceptance Criteria

- **Auth:** Login with password, session persists across page refreshes, logout works, unauthenticated requests redirect to login
- **Categories:** Create, read, update, delete categories; categories appear in transaction forms
- **Transactions:** Add ingress/egress with category, date, amount, description; edit and delete; list with filtering
- **Dashboard:** Monthly view shows income vs. expenses bar chart, net balance card, yearly view shows monthly trend line; data reflects actual transactions
- **Contacts:** Create, edit, delete contacts; contacts reusable across subscriptions and debts
- **Subscriptions:** Create personal and shared; assign members from contacts; scheduler generates pending payment records; record payments per member per period
- **Debts:** Create individual invoices per debtor; record partial payments; auto-created ingress appears in transaction list and dashboard
- **Public view:** UUID link opens without login; shows all members' payment status for that subscription; 404 for invalid tokens
- **Responsive:** All views usable at 390px (iPhone) and 1440px (desktop) widths
- **Deployment:** Running on Railway with HTTPS, PostgreSQL provisioned, all features working

## Open Questions

- Exact Railway service configuration (Dockerfile vs. Nixpacks for Quarkus) — user has prior deployment experience, will verify during S08
