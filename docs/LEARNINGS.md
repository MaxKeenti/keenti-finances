# Learnings

Project-level patterns, lessons, and surprises worth remembering across milestones. Append as you discover things; promote sticky patterns to ADRs only if they meet the [ADR bar](./adr/).

Seed content extracted from `docs/archive/gsd-snapshot/milestones/M001/M001-LEARNINGS.md` (M001 retrospective, May 2026). Obsolete entries (e.g. lessons specific to the pre-WorkOS HMAC session cookie) have been dropped; sticky ones kept.

---

## Patterns

- **Hexagonal CRUD vertical slice.** Domain POJO → port interfaces → application service → Panache adapter → JAX-RS resource → SvelteKit server action → superforms UI. The REST resource enriches responses by calling use-case ports for related entity names rather than reaching into JPQL joins — keeps the domain layer clean.

- **Auto-side-effect via use-case port from another service.** When a domain operation must produce a Transaction as a side effect (canonical example: `DebtService.recordPayment()` → `TransactionUseCase.create(INGRESS)`, see ADR-0005), call the other use case through its port. Don't reach into the other service's entities directly.

- **Idempotent date-check scheduler.** `@Scheduled` jobs check rows for a target state (e.g. "billing date within 7 days AND no Payment Record exists yet") rather than time-since-last-run. Restart-safe, manual-trigger-safe, missed-run-safe. Canonical implementation: `SubscriptionBillingScheduler` (ADR-0006).

- **Native SQL `SUM` via `EntityManager` for aggregation.** Panache repositories use `EntityManager` native SQL for `SUM`/`EXTRACT`/aggregation queries to avoid loading all rows into memory and to keep domain models free of ORM annotations. Established in `PanacheTransactionRepository`, reused in `PanacheDebtPaymentRepository`. Remember: these are the three native SQL queries that bypass the userScope Hibernate filter (ADR-0012) and need an explicit `WHERE user_id = ?`.

- **Zero-fill at the repository layer for dashboard aggregation.** Zero-filling all 12 months in the repository (not the service) ensures the service always receives a complete 12-element list regardless of data sparsity. Simpler service code, no nil-checking.

- **SVG charting with `d3-scale` in Svelte 5.** Use `d3-scale` for scale math (`scaleLinear`, `scaleBand`, etc.) and render charts as inline SVG inside a Svelte component. Avoids any third-party charting library's Svelte 5 compatibility constraints.

- **`PUBLIC_PATHS` prefix bypass in `hooks.server.ts`.** Adding a path prefix (e.g. `/public`) to `PUBLIC_PATHS` covers every sub-route under it without per-file changes. Future unauthenticated routes should follow this pattern. Any route that performs session cleanup (like `/logout`) must be in `PUBLIC_PATHS` — otherwise the auth guard redirects before the load function can clear the cookie, causing an infinite redirect loop.

- **Unauthenticated Quarkus endpoint with nested Java records.** Omit `@RolesAllowed` / `@Authenticated` (canonical example: `PublicSubscriptionResource`) and use nested `record` types for composite response DTOs. Clean JSON serialization with no extra DTO classes.

- **Sibling `@Path` resource over JAX-RS sub-resource locators.** When a nested resource needs more than one HTTP verb, implement it as a sibling `@Path` class. Equivalent REST contract, significantly simpler wiring. Established for the subscription members + payments endpoints.

- **Quarkus `%prod` profile for env-var-driven config.** Prefix every production-only property with `%prod.` so `application.properties` works for local dev with localhost defaults *and* Railway deployment with env vars — zero local env setup needed.

## Lessons

- **TypeScript: SvelteKit requires `moduleResolution: bundler`.** `NodeNext` breaks `$lib` aliases and the virtual `./$types` modules. Always use bundler mode for SvelteKit projects.

- **Git worktrees do not share `node_modules`.** Run `bun install` in each worktree before running type checks or builds. Standard worktree behaviour, not a bug.

- **Svelte 5: do not wrap `data.form` in a getter when calling `superForm()`.** Svelte 5 `$props()` returns are already reactive. A getter wrapper `() => data.form` breaks the TypeScript signature inferred by superforms. Pass `data.form` directly.

- **Font imports in CSS must be listed in `package.json`.** Bundlers don't auto-detect CSS-referenced font packages. If `layout.css` does `@import "@fontsource-variable/fraunces"`, the package must be in `dependencies`.

- **`svelte-check` output can be drowned by node_modules noise.** The `Effect` library's `SubscriptionRef` TypeScript files generate 10,000+ spurious errors in this project. Filter `svelte-check` output by route path to isolate real errors.

## Surprises (read these before adopting the obvious thing)

- **Layerchart v1.0.13 exports Svelte 4 component types** (`$$Props`, `SvelteComponentTyped`) that break `svelte-check` in Svelte 5 projects. The library was selected in M001 (D003), installed, found unusable, then removed in M002 (D021). Use `d3-scale` directly until layerchart ships native Svelte 5 exports. Don't trust shadcn-svelte's recommendation here without verifying the version.

- **Effect library noise dominates `svelte-check`.** This is pre-existing in `node_modules` — not introduced by any feature. Filter by route prefix when diagnosing real type errors.
