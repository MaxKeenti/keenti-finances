# Copilot instructions for Keenti Finances

## Build, test, and lint
Backend (Quarkus)
- Dev server: `cd backend && ./mvnw quarkus:dev`
- Build/package: `cd backend && ./mvnw package`
- Tests: `cd backend && ./mvnw test`
- Single test: `cd backend && ./mvnw -Dtest=MyTest test` (or `MyTest#method`)

Frontend (SvelteKit)
- Dev server: `cd frontend && npm run dev`
- Build: `cd frontend && npm run build`
- Typecheck: `cd frontend && npm run check`

## High-level architecture
- Monorepo with `frontend/` (SvelteKit) and `backend/` (Quarkus). Railway deploys two services with private networking; Quarkus has no public ingress.
- SvelteKit owns auth (WorkOS passkey flow) and session handling in `frontend/src/hooks.server.ts`.
- All `/api/*` traffic proxies through `frontend/src/routes/api/[...path]/+server.ts` to `BACKEND_URL`; `handleFetch` injects `X-WorkOS-User-Id` for server-side fetches.
- Quarkus follows hexagonal architecture: `domain/` (models + ports), `application/service/` (use cases), `infrastructure/adapter/` (REST + persistence).
- Tenant isolation in Quarkus is enforced by Hibernate filters (`userScope`, `softDelete`) enabled by `UserScopeFilter` using `X-WorkOS-User-Id`.
- Database schema changes are managed by Flyway migrations in `backend/src/main/resources/db/migration`.

## Key conventions
- Domain vocabulary is defined in `CONTEXT.md` (use Transaction + Direction `INGRESS/EGRESS`, Subscription Member vs User, Payment Record, Owner Participation).
- `user_id` exists only on root entities (Category, Contact, Transaction, Subscription, Debt). Child entities inherit scope; domain POJOs do not carry `userId`.
- Soft-delete uses `deleted_at` filters. Do not cascade soft-delete across root entities; linked Transactions remain even if the Debt/Subscription is deleted.
- Category color is stored as an OKLCH hue; frontend combines hue with fixed lightness/chroma per theme.
- Debt Payment creation also creates an INGRESS Transaction; deletions must remove the linked auto-created Transaction via the use case.
- Subscription billing scheduler is daily and idempotent (creates Payment Records for the next 7 days) and runs without `userScope`, so it must set `user_id` manually.
- Transaction↔Subscription is a nullable FK used for retroactive tagging only; no synthetic Transactions are created when linking.
- Issue tracking uses the `gh` CLI; canonical triage labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`.
- Architectural decisions live in `docs/adr/` and should be treated as the source of truth for cross-cutting behavior.
