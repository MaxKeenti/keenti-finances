---
phase: M001
phase_name: Personal Finance Tracker
project: keenti-finances
generated: "2026-05-14T21:00:00Z"
counts:
  decisions: 4
  lessons: 8
  patterns: 10
  surprises: 5
missing_artifacts: []
---

# M001 Learnings

## Decision Re-evaluation

| Decision | What Shipped | Status | Revisit? |
|---|---|---|---|
| D001: Hexagonal architecture (Quarkus) | Applied consistently across all 8 slices. Domain POJOs have zero framework imports confirmed by grep checks in every slice. | Validated | No |
| D002: SvelteKit owns session | Implemented as HMAC-SHA256 signed HTTP-only cookie. Works cleanly. Quarkus is a pure internal API. | Validated | No |
| D003: Layerchart via shadcn-svelte | Layerchart installed but NOT used — v1.0.13 exports Svelte 4 API ($$Props, SvelteComponentTyped) that fails svelte-check in Svelte 5. d3-scale used directly instead. | Deviated | Yes — revisit when layerchart ships Svelte 5 exports |
| D004: Simple password auth, passkeys deferred | Implemented as planned. No issues. | Validated | No |
| D005: Debt payments auto-create ingress transactions | Implemented in DebtService.recordPayment() calling TransactionUseCase.create(INGRESS). Works cleanly. | Validated | No |
| D006: @Scheduled daily billing scheduler | Implemented with cron "0 0 1 * * ?". Code compiles. Runtime execution unverified (no live deployment). | Validated (code) | No |
| D007: SvelteKit proxy pattern | Held throughout. Extended with configurable BACKEND_URL in S08 for Railway private networking. | Validated | No |
| D008: Hexagonal package naming | Applied consistently. | Validated | No |
| D009: HMAC-SHA256 session cookie | Held. SESSION_SECRET required lazy enforcement in S08 (eager throw broke vite build). | Validated | No |
| D010: Catch-all +server.ts proxy | Held. Scales to all API routes. | Validated | No |
| D011: Railway deployment topology | Artifacts produced (Dockerfiles, DEPLOY.md, health probes, both builds pass). Actual Railway provisioning deferred as follow-up — requires external infrastructure setup. | Partial | No — continue in next milestone |

---

### Decisions

- **Layerchart was selected for charting but could not be used in Svelte 5.** Layerchart v1.0.13 still exports Svelte 4 component types ($$Props, SvelteComponentTyped) which cause type errors under svelte-check in a Svelte 5 project. d3-scale was used directly for scale math with inline SVG rendering. The layerchart dependency remains installed for when the library ships Svelte 5 native exports.
  Source: S04-SUMMARY.md/key_decisions

- **SESSION_SECRET must be enforced lazily (at request time, not module load time).** SvelteKit's post-build analysis imports server modules with NODE_ENV=production. An eager `throw` at module top-level breaks `vite build`. The fix: wrap the validation inside `getSessionSecret()` so it only executes on the first real request.
  Source: S08-SUMMARY.md/key_decisions

- **PaymentRecordResource implemented as a sibling @Path class rather than a JAX-RS sub-resource.** Sibling @Path classes avoid sub-resource locator complexity while providing an identical REST contract. Applied for subscriptions nested endpoints (members + payments).
  Source: S05-SUMMARY.md/key_decisions

- **Debt status transitions to PAID inline within recordPayment() rather than via a scheduled reconciliation.** Immediate consistency avoids the complexity of a reconciliation job and makes the state change atomic with the write operation.
  Source: S06-SUMMARY.md/key_decisions

---

### Lessons

- **Layerchart v1.0.13 is Svelte 4 only — do not use its component API in Svelte 5 projects.** Use d3-scale directly for chart math and render as inline SVG. Watch for a Svelte 5 release of layerchart before adopting its components.
  Source: S04-SUMMARY.md/Deviations

- **SvelteKit requires `moduleResolution: bundler` — NodeNext breaks $lib aliases and virtual ./$types modules.** The scaffolded tsconfig had `module: NodeNext` + `moduleResolution: NodeNext` which caused all $lib path aliases and virtual ./$types modules to fail type checking. Always use bundler mode for SvelteKit.
  Source: S01-SUMMARY.md/Deviations

- **Git worktrees do not share node_modules — run `bun install` in each worktree before running type checks.** bun install was required before bun run check could succeed in the M001 worktree. Standard git worktree behavior; not a bug.
  Source: S02-SUMMARY.md/Deviations

- **`/logout` must be added to PUBLIC_PATHS in hooks.server.ts.** Without it, the auth guard redirects unauthenticated requests before the logout load can clear the cookie, causing an infinite redirect loop. Any route that performs session cleanup must be public.
  Source: S01-SUMMARY.md/key_decisions

- **`superForm()` in Svelte 5: do not wrap `data.form` in a getter function.** Svelte 5 `$props()` returns are already reactive. A getter wrapper `() => data.form` breaks the TypeScript signature inferred by superforms. Pass `data.form` directly.
  Source: S08-SUMMARY.md/Deviations

- **Font dependencies referenced in CSS must be explicitly listed in package.json.** `@fontsource-variable/fraunces` was referenced in layout.css but missing from package.json, causing `bun run build` to fail. Font imports in CSS are not auto-detected by the bundler dependency resolver.
  Source: S08-SUMMARY.md/What Happened

- **The Effect library in node_modules generates 10,000+ svelte-check errors that mask real errors.** These come from Effect's SubscriptionRef TypeScript files. Filter svelte-check output by route path to distinguish real errors from library noise.
  Source: S05-SUMMARY.md/Verification

- **Quarkus `%prod` profile prefix leaves local dev env-var-free.** All production DB and config values can be prefixed with `%prod.` so `application.properties` works for local dev with localhost defaults and Railway deployment with env vars — zero local env setup needed.
  Source: S08-SUMMARY.md/key_decisions

---

### Patterns

- **Hexagonal CRUD vertical slice pattern: domain POJO → port interfaces → application service → Panache adapter → JAX-RS resource → SvelteKit server action → superforms UI.** Established in S02 and applied consistently through S06. The REST resource enriches responses by calling use-case ports for related entity names — no JPQL joins, keeping domain layer clean.
  Source: S02-SUMMARY.md/patterns_established, S03-SUMMARY.md/patterns_established

- **Debt payment auto-INGRESS: DebtService.recordPayment() calls TransactionUseCase.create() with direction=INGRESS.** Eliminates manual double-entry. Extend this pattern for any future domain operation that must automatically generate a financial transaction as a side effect.
  Source: S06-SUMMARY.md/patterns_established

- **Daily billing scheduler pattern: @Scheduled cron, idempotent billing-date check, 7-day lead generation.** The scheduler checks billing dates (not time since last run) so restarts are safe. Generates PENDING payment records 7 days before billing date so members see upcoming dues.
  Source: S05-SUMMARY.md/key_decisions

- **Native SQL SUM via EntityManager for aggregation in Panache repositories.** Use EntityManager native SQL queries for aggregations (SUM, EXTRACT) to avoid loading all records into memory and to keep domain models free of ORM annotations. Pattern established in PanacheTransactionRepository and reused in PanacheDebtPaymentRepository.
  Source: S04-SUMMARY.md/key_decisions, S06-SUMMARY.md/patterns_established

- **12-month zero-fill at the repository layer for dashboard aggregation.** Zero-filling all 12 months in the repository (not the service) ensures the service always receives a complete 12-element list regardless of data sparsity — simpler service assembly, no nil-checking.
  Source: S04-SUMMARY.md/key_decisions

- **SVG charting with d3-scale in Svelte 5.** Use d3-scale for scale math (scaleLinear, scaleBand, etc.) and render charts as inline SVG markup inside a Svelte component. Avoids any third-party charting library's Svelte compatibility constraints.
  Source: S04-SUMMARY.md/patterns_established

- **SvelteKit PUBLIC_PATHS prefix bypass.** Adding a path prefix (e.g. '/public') to PUBLIC_PATHS in hooks.server.ts covers all sub-routes under that prefix without per-file changes. All future unauthenticated routes should follow this pattern.
  Source: S07-SUMMARY.md/key_decisions

- **Unauthenticated Quarkus endpoint: omit @RolesAllowed/@Authenticated, use nested Java records for composite response DTOs.** Applied in PublicSubscriptionResource with nested MemberPaymentSummary and PaymentSummary records — clean JSON serialization without extra DTO classes.
  Source: S07-SUMMARY.md/patterns_established

- **Env-var-driven production config via Quarkus %prod profile.** Use `%prod.quarkus.*` prefix for all production-only config. Dev defaults remain in the unprefixed block. No env vars needed for local development — any property without a profile prefix is the dev default.
  Source: S08-SUMMARY.md/patterns_established

- **Sibling @Path resource for nested endpoints.** When a nested resource needs more than one HTTP verb, implement it as a sibling `@Path` class rather than a JAX-RS sub-resource locator. Equivalent REST contract with significantly simpler wiring.
  Source: S05-SUMMARY.md/key_decisions

---

### Surprises

- **Layerchart v1.0.13 exports Svelte 4 API that breaks Svelte 5 type checking.** D003 selected layerchart as the charting library; it was installed but could not be used. d3-scale was substituted. This was discovered during S04 implementation, not upfront.
  Source: S04-SUMMARY.md/Deviations

- **SESSION_SECRET eager enforcement broke `vite build`.** SvelteKit imports server modules with NODE_ENV=production during its post-build analysis phase. An eager `throw new Error('SESSION_SECRET not set')` at module scope caused the build to fail. Discovered in S08.
  Source: S08-SUMMARY.md/What Happened

- **@fontsource-variable/fraunces was missing from package.json but referenced in CSS.** This caused `bun run build` to fail in S08. Font imports in CSS are not auto-detected by the dependency resolver.
  Source: S08-SUMMARY.md/What Happened

- **The Effect library generates 10,000+ spurious svelte-check errors in this project.** These come from Effect's SubscriptionRef TypeScript files in node_modules. They pre-exist and were not introduced by any slice — but they dominated svelte-check output, requiring per-route grep to isolate real errors.
  Source: S05-SUMMARY.md/Verification

- **superForm() getter wrapper broke TypeScript in Svelte 5.** A pre-existing pattern of `superForm(() => data.form)` was treated as an error by the S08 build. Svelte 5 $props() returns are already reactive — the getter is unnecessary and breaks the inferred type signature.
  Source: S08-SUMMARY.md/Deviations
