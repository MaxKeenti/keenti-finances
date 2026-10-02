# Keenti Finances - Development Standards & Conventions

This document outlines the architectural, structural, and coding standards for Keenti Finances. All AI assistants and developers must adhere to these guidelines to ensure consistency across the codebase.

## 1. Architecture Overview
- **Monorepo Structure**: `backend/` (Quarkus monolith), `frontend/` (SvelteKit), `docs/` (ADRs, feature notes, agent guides). No docker-compose; each app runs on its own.
- **Backend**: Java 21 + Quarkus, one service, **Hexagonal Architecture** (ADR-0001).
- **Frontend**: Svelte 5 + SvelteKit on `adapter-node`. It owns authentication (WorkOS passkeys, ADR-0002/0004) and proxies every `/api/*` call to the backend (ADR-0003).
- **Database**: PostgreSQL, schema managed by Flyway.
- **Deployment**: Railway, two services + PostgreSQL (ADR-0007, `DEPLOY.md`). The backend has **no auth of its own** and must never get public ingress — tenant isolation depends on the `X-WorkOS-User-Id` header the frontend proxy injects (ADR-0013).
- **Domain language**: `CONTEXT.md` is the glossary. Use its terms (Box, Available to Spend, Debt Payment, Payment Record, Direction, …) in code, tests, issues, and commit messages. Read the ADRs in `docs/adr/` that touch the area before changing it, and flag any contradiction explicitly instead of silently overriding it.

## 2. Backend Standards (Java Quarkus)

### Architecture Layers (`com.keenti.finances`)
- **`domain/model`**: Plain Java domain objects and pure calculators (e.g. `PlanningPreviewCalculator`). No Quarkus, JPA, or Jakarta annotations.
- **`domain/port/in`**: Use-case interfaces (`*UseCase`). **`domain/port/out`**: repository and provider interfaces.
- **`application/service`**: Implements the use cases. A service that needs another aggregate's behaviour calls that aggregate's **use-case port**, never its entities or repository (e.g. `DebtService` → `TransactionUseCase`, ADR-0005).
- **`infrastructure/adapter/in/rest`**: JAX-RS resources plus request/response records. For nested resources that need several verbs, prefer a sibling `@Path` class over sub-resource locators.
- **`infrastructure/adapter/out/persistence`**: `*Entity` (Panache, public fields) and `Panache*Repository` adapters that map to domain models.

### Multi-tenancy & Soft Delete
- **User scope lives on root entities only** (ADR-0014). Root entities carry `user_id` and the `userScope` Hibernate `@Filter`; child entities inherit scope through their parent.
- **Filter activation** goes through the CDI interceptor on repositories (ADR-0012, ADR-0018). Do not call `enableFilter` by hand in services or resources.
- **Native SQL bypasses Hibernate filters.** Any `EntityManager` native query must add an explicit `WHERE user_id = ?` (and `deleted_at IS NULL` where it applies).
- **Soft delete** is `deleted_at` on root entities. It does **not** cascade across root-entity boundaries (ADR-0016). Unique constraints on soft-deletable columns use partial indexes (ADR-0015).
- New root entities must plug into Trash (list-deleted, restore, permanent delete).

### Database & Migrations
- Flyway files in `backend/src/main/resources/db/migration/` are **append-only once deployed** — not even whitespace or comments may change. Ship `V{n+1}__*.sql` instead. Read `docs/agents/migrations.md` before touching migrations.
- Next version = highest existing `Vnn` + 1. Sort numerically, not lexically (`V10` > `V9`). Rebase your number if another PR claims it first.
- `snake_case` tables and columns. IDs are `BIGINT` identity (`Long`), not UUIDs.
- Money is MXN-only. Never use `double`/`float` for amounts.

### Configuration & Scheduling
- Production-only properties use the `%prod.` prefix in `application.properties`, so local dev runs with localhost defaults and no env setup.
- Billing is a manual per-Subscription trigger (ADR-0019), not a cron job. Any date-driven job must be idempotent: check whether the target state already exists, not how long it has been since the last run.

### Testing
- `@QuarkusTest` boots a real database and runs every migration, so no separate "migration works" test is needed.
- CI runs tests with `TZ=America/Mexico_City`. Date-sensitive fixtures must hold in that zone and in UTC; never assume the runner's local day.
- Verify with `./mvnw verify` from `backend/`.

## 3. Frontend Standards (Svelte 5 & SvelteKit)

### Tooling
- Use **bun** for everything (`bun install`, `bun run dev`, `bun run check`, `bun run build`, `bun run test`). `bun.lock` is committed; CI installs with `--frozen-lockfile`.
- Tests are `bun test` files in `frontend/tests/` (preloaded by `tests/setup.ts`). Fixtures live in `tests/fixtures/`.
- `svelte-check` can be drowned by noise from `node_modules` (Effect types). Filter its output by the paths you touched.
- Git worktrees do not share `node_modules`, so run `bun install` in each worktree.

### Structure & Data Flow
- **Routes** in `src/routes/<feature>/` use `+page.server.ts` for `load` and form `actions`. Server code reads the backend through `BACKEND_URL` with the session's bearer token. The browser only ever calls `/api/*`, which `src/routes/api/[...path]/+server.ts` proxies.
- **Unavailable is not zero.** Load independent page sections with `loadSection` (`$lib/server/section-load`) into a `Section<T>` (`$lib/types/section`). A failed load renders as unavailable (`SectionUnavailable`). Never fall back to `0`, `[]`, or "no records": those are claims about the User's money.
- Validate backend payloads with the parsers in `$lib/server/payloads.ts` before trusting them.
- **Auth**: `hooks.server.ts` guards every route except `PUBLIC_PATHS`. A new unauthenticated route (or one that clears the session, such as `/logout`) must be covered by a `PUBLIC_PATHS` prefix. `TEST_AUTH_BYPASS` is for local development only and is refused in production.
- **Shared logic** sits at the root of `src/lib/` (`formatting.ts`, `obligation-status.ts`, `planning-preview.ts`, `*-labels.ts`, …). Keep it pure and cover it with tests in `frontend/tests/`.

### Forms
- Use `sveltekit-superforms` with `zod` (`zod4` adapter on the server, `zod4Client` on the client) and the single-dialog pattern that existing pages use.
- Pass `data.form` to `superForm()` directly, or through `untrack(() => data.form)`. Never wrap it in a getter.
- Report success and error with `toast` from `svelte-sonner`, then `invalidateAll()`.

### Components & UI
- `src/lib/components/ui/` contains **shadcn-svelte generated primitives only**. Hand-written components go in `src/lib/components/<feature>/` and are exported through that folder's `index.ts` barrel.
- Component files are `kebab-case.svelte`.
- **Never** call `confirm()` or `alert()`. Use `adaptive-confirm` (`submitWithAdaptiveConfirm`). For pickers, use the adaptive `native-select` / `native-date-picker` wrappers, which render native controls on mobile and shadcn on desktop.
- Styling uses **Tailwind CSS v4 only**, with no `<style>` blocks. `src/routes/layout.css` is the theme boundary: design tokens, per-User primary hue and fonts.
- **Colour comes only from semantic tokens**, never from raw palette classes (`bg-red-500`, `text-amber-600`, `bg-white`, `shadow-black/10`) or arbitrary hex/rgb values. The tokens are:
  - `primary`, `muted`, `destructive`, and the other shadcn tokens
  - `money-positive` / `money-negative` and `money-owed-to-you` for money direction
  - `success`, `warning`, `info`, `highlight`, and `transfer` for status and marks, each with a `-text` variant for readable foregrounds. Tint a background with opacity, e.g. `bg-success/15 text-success-text`.
  - `chart-*` for chart marks
  - `scrim` for overlays, shadows, and hairline rings

  Each token carries its own dark-mode value, so `dark:` colour overrides are unnecessary. If nothing fits, add a token to both `:root` and `.dark` in `layout.css` and register it in `@theme inline`. `bun run lint:colors` enforces this and runs before every `bun run build`. When it flags something, use or add a token; do not relax the gate.
- Category, Box, and Financial Account colours are a User-chosen OKLCH hue (ADR-0008, ADR-0017). Store and pass the hue, not hex or palette names. The `oklch(… var(--*-hue))` arbitrary values that render these hues are the only sanctioned exception to the token rule.
- Charts use `d3-scale` and inline SVG (see `docs/LEARNINGS.md` for why not layerchart), with an `sr-only` text equivalent.
- **Accessibility**: icon-only buttons need `aria-label`, decorative icons get `aria-hidden="true"`, and every `<label>` is tied to its control.

### i18n & Formatting
- All user-facing text goes through Paraglide (`m.*()` from `$lib/paraglide/messages.js`). Add each key to both `messages/en.json` and `messages/es.json`. This includes zod validation messages.
- Format money only with `mxnFormatter(locale)`, and dates only with the helpers in `$lib/formatting.ts`. Never hand-build currency strings or hard-code a locale.
- Never render raw backend identifiers (enum codes, ISO timestamps, field names). Map them through the `*-labels.ts` helpers.

### Svelte 5 Rules
- Use runes (`$state`, `$derived`, `$effect`, `$props`). Do not use `<svelte:component>`, `export let`, or `$:`.
- `{@const}` must be a direct child of a block (`{#each}`, `{#if}`, …).
- Runes cannot be used in `<script module>`. Put module-level reactive state in a `.svelte.ts` file.
- Don't self-close non-void HTML elements (`<div></div>`, not `<div />`).

## 4. Workflows & General Rules
- **Before committing**, run the checks for each side you touched:
  - backend: `./mvnw verify`
  - frontend: `bun run check && bun run build && bun run test`

  CI (`.github/workflows/ci.yml`) runs the same commands and also builds both Docker images.
- Commit messages use conventional prefixes (`feat:`, `fix:`, `test:`, `docs:`, `chore:`). Work happens on a branch and merges through a PR, not directly on `main`.
- Issues and triage follow `docs/agents/issue-tracker.md` and `docs/agents/triage-labels.md`.
- Documentation map:
  - `CONTEXT.md`: glossary
  - `docs/adr/`: hard-to-reverse decisions
  - `docs/features/`: current behaviour per feature
  - `docs/decisions/`: product decisions
  - `docs/CAPABILITIES.md`: what the product does and doesn't do
  - `docs/LEARNINGS.md`: patterns and gotchas
  - `DEPLOY.md`: Railway setup
  - `docs/operations/`: incident notes
- `docs/archive/` (including the GSD snapshot) is **read-only, non-canonical history**. Mine it for context, and record durable conclusions in the canonical docs above. Do not reintroduce GSD bundles, `.gsd`, or `.mcp.json` into the repo.
- Never commit `.env` files or secrets. Railway variables are managed in Railway.
