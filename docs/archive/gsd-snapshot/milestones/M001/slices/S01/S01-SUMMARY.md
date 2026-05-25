---
id: S01
parent: M001
milestone: M001
provides:
  - Hexagonal package structure (domain/application/infrastructure layers) for all future slices
  - Auth middleware and session management on SvelteKit
  - SvelteKit ↔ Quarkus proxy pattern via catch-all +server.ts
  - Flyway migration infrastructure (V1 creates app_user table with seeded admin)
  - Base REST adapter pattern: JAX-RS @Path resource backed by hexagonal application service
requires:
  []
affects:
  []
key_files:
  - (none)
key_decisions:
  - app_user table name (not user) to avoid conflict with PostgreSQL reserved word USER
  - bcrypt cost factor 10 for dev seed hash — meets ≥10 threat surface requirement
  - Domain model User is a plain POJO with no Jakarta/Quarkus imports — hexagonal purity enforced at compile time
  - AuthService uses Jboss Logger (not SLF4J) for structured login audit logs — matches Quarkus idiom
  - LoginResponse exposes only username — password hash never leaves the service layer
  - timingSafeEqual used for cookie validation to prevent timing-based forgery attacks
  - SESSION_SECRET has a dev fallback so local development works without env configuration
  - Removed NodeNext module/moduleResolution from tsconfig.json — SvelteKit requires bundler resolution; NodeNext breaks $lib aliases and virtual ./$types modules
  - /logout added to PUBLIC_PATHS in hooks.server.ts — without it, auth guard redirects unauthenticated requests before the logout load can clear the cookie, causing an infinite redirect loop
  - Logout implemented as a load function (not a form action) — href navigation is a GET; no form POST needed
patterns_established:
  - Hexagonal package layout: domain/model, domain/port/in, domain/port/out, application/service, infrastructure/adapter/in/rest, infrastructure/adapter/out/persistence, infrastructure/adapter/out/security
  - SvelteKit catch-all proxy pattern: src/routes/api/[...path]/+server.ts forwards all HTTP methods to Quarkus http://localhost:8080
  - HMAC-SHA256 signed HTTP-only session cookie pattern — sign on login, timingSafeEqual verify on every request
  - Conditional app shell rendering in +layout.svelte: AppShell wraps children only when data.session is truthy
  - Responsive nav via Tailwind sm: breakpoints: sidebar hidden/flex below sm, bottom-nav flex/hidden above sm
observability_surfaces:
  - AuthService emits structured Jboss log lines on every login attempt: username, success/failure flag, timestamp
  - SvelteKit login/+page.server.ts logs login success/failure with username and timestamp
  - Proxy returns 502 on Quarkus connection failure and 504 on 30s timeout — distinguishable error codes for monitoring
drill_down_paths:
  []
duration: ""
verification_result: passed
completed_at: 2026-05-13T20:38:36.815Z
blocker_discovered: false
---

# S01: Auth & Hexagonal Foundation

**Password auth, HMAC-signed session cookies, hexagonal Quarkus backend, SvelteKit proxy, and responsive app shell all wired and type-checking clean.**

## What Happened

Four tasks delivered the full auth and infrastructure foundation:

**T01 — Hexagonal backend scaffolded**: Renamed the Maven artifact to `com.keenti.finances/keenti-finances`, added Quarkus REST Jackson, Hibernate ORM Panache, Hibernate Validator, jbcrypt, Flyway, and JDBC PostgreSQL dependencies to `pom.xml`. Created the full hexagonal package tree (domain/model, domain/port/in, domain/port/out, application/service, infrastructure/adapter/in/rest, infrastructure/adapter/out/persistence, infrastructure/adapter/out/security) with `.gitkeep` sentinels. Configured `application.properties` for dev Postgres and Flyway auto-migrate. Created `V1__create_user_table.sql` with an `app_user` table (BIGSERIAL id, UNIQUE username, password_hash) and a bcrypt cost-10 seeded admin user. Deleted the default `GreetingResource` and its tests. `./mvnw compile -q` exits 0.

**T02 — Auth vertical through all hexagonal layers**: Created 11 Java files: `User` POJO (no framework imports), `AuthUseCase` inbound port, `UserRepository` and `PasswordHasher` outbound ports, `AuthService` (@ApplicationScoped, Jboss Logger for structured login audit logging), `UserEntity` (Panache @Entity on app_user), `PanacheUserRepository`, `BcryptPasswordHasher` (delegates to `BCrypt.checkpw`), `LoginRequest` (@NotBlank validation), `LoginResponse` (username only — hash never leaves service layer), `AuthResource` (@Path("/api/auth") POST /login, 200 + LoginResponse or 401 + JSON error body). Compilation passes. Domain layer has no jakarta/javax/quarkus imports.

**T03 — SvelteKit proxy, session, auth guard, and login page**: Installed zod v4.4.3 and @types/node. Fixed a pre-existing tsconfig.json misconfiguration (removed `module: NodeNext` and `moduleResolution: NodeNext` which conflict with SvelteKit's required bundler resolution and break $lib aliases). Created: `session.ts` (HMAC-SHA256 sign/verify with `timingSafeEqual` to prevent timing attacks; SESSION_SECRET env var with dev fallback), catch-all `api/[...path]/+server.ts` proxy forwarding all HTTP methods to Quarkus with 502 on connection failure and 504 on 30s timeout, `app.d.ts` with `App.Locals.session`, `hooks.server.ts` auth guard (exempts /login, /api/auth/login, /_app/, /static/), `login/+page.server.ts` (Zod schema, superValidate, POSTs JSON to Quarkus, sets HTTP-only SameSite=Lax cookie on 200, fail on 401), `login/+page.svelte` (centered Card, formsnap Field/Control/FieldErrors, submitting state), `+layout.server.ts` (passes session to page data). `bun run check`: 0 errors, 1 expected warning.

**T04 — Responsive app shell with sidebar and bottom nav**: Created `sidebar.svelte` (fixed 240px left panel, hidden below sm, Lucide icons, active-route highlighting via `$page.url.pathname`, logout link), `bottom-nav.svelte` (fixed bottom bar, visible only below sm, 4 nav items + logout), `app-shell.svelte` (responsive wrapper, sm:ml-60 sidebar offset, pb-16 sm:pb-0 bottom-nav clearance). Updated `+layout.svelte` to conditionally wrap in AppShell when session is present. Created dashboard `+page.svelte` (shadcn Card placeholder). Created `logout/+page.server.ts` as a load function (GET-triggered href) that deletes the session cookie and redirects to /login. Added /logout to PUBLIC_PATHS in `hooks.server.ts` to prevent auth-guard redirect loop after cookie is cleared. `bun run check`: 0 errors.

## Verification

Backend: `./mvnw compile -q` exits 0. Migration file `V1__create_user_table.sql` present. `quarkus-hibernate-orm-panache` in pom.xml. `GreetingResource.java` deleted. `BCrypt.checkpw` present in `BcryptPasswordHasher.java`. `@Path` annotation present in `AuthResource.java`. Domain layer grep for jakarta/javax/quarkus imports returns exit 1 (clean). Frontend: `bun run check` (svelte-kit sync + svelte-check): 0 errors, 1 expected warning (superForm initial value capture on login page — benign). All required files present: hooks.server.ts, api/[...path]/+server.ts, login/+page.svelte, app-shell/app-shell.svelte, logout/+page.server.ts. SESSION_SECRET referenced in session.ts.

## Requirements Advanced

None.

## Requirements Validated

- R001 — POST /api/auth/login with bcrypt verification compiles clean. HMAC-SHA256 signed HTTP-only session cookie set on 200. Auth guard in hooks.server.ts redirects unauthenticated requests to /login. timingSafeEqual prevents timing attacks. bun run check: 0 errors.

## New Requirements Surfaced

None.

## Requirements Invalidated or Re-scoped

None.

## Operational Readiness

None.

## Deviations

tsconfig.json: Removed pre-existing `module: NodeNext` and `moduleResolution: NodeNext` settings — these conflicted with SvelteKit's required bundler module resolution and caused all $lib path aliases and virtual ./$types modules to fail type checking. This was a correction to the scaffolded configuration, not a new requirement.

## Known Limitations

Integration UAT requires live PostgreSQL and both services running locally. The 1 Svelte warning on the login page (superForm initial value capture) is a cosmetic framework advisory, not a functional issue.

## Follow-ups

None.

## Files Created/Modified

- `backend/pom.xml` — Added Panache, jbcrypt, REST Jackson, Hibernate Validator deps; renamed groupId/artifactId
- `backend/src/main/resources/application.properties` — Configured dev Postgres datasource and Flyway auto-migrate
- `backend/src/main/resources/db/migration/V1__create_user_table.sql` — Creates app_user table, seeds bcrypt-hashed admin user
- `backend/src/main/java/com/keenti/finances/domain/model/User.java` — Plain POJO — no framework imports
- `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java` — Inbound port interface
- `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java` — Outbound repository port
- `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java` — Outbound password hasher port
- `backend/src/main/java/com/keenti/finances/application/service/AuthService.java` — Login logic with structured audit logging
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java` — Panache entity for app_user
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java` — Maps entity to domain User
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java` — Delegates to BCrypt.checkpw
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java` — JAX-RS POST /api/auth/login, 200 or 401
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java` — Request record with @NotBlank validation
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java` — Response record — username only
- `frontend/src/lib/server/session.ts` — HMAC-SHA256 sign/verify with timingSafeEqual
- `frontend/src/routes/api/[...path]/+server.ts` — Catch-all proxy to Quarkus
- `frontend/src/app.d.ts` — App.Locals.session and App.PageData.session types
- `frontend/src/hooks.server.ts` — Auth guard, session validation, PUBLIC_PATHS including /logout
- `frontend/src/routes/login/+page.server.ts` — Zod schema, superValidate, POSTs to Quarkus, sets cookie
- `frontend/src/routes/login/+page.svelte` — Centered Card with formsnap validation
- `frontend/src/routes/+layout.server.ts` — Passes session to page data
- `frontend/src/routes/+layout.svelte` — Conditionally wraps children in AppShell
- `frontend/src/routes/+page.svelte` — Dashboard placeholder Card
- `frontend/src/routes/logout/+page.server.ts` — Clears session cookie, redirects to /login
- `frontend/src/lib/components/app-shell/sidebar.svelte` — Desktop sidebar nav, hidden below sm
- `frontend/src/lib/components/app-shell/bottom-nav.svelte` — Mobile bottom tab bar, hidden above sm
- `frontend/src/lib/components/app-shell/app-shell.svelte` — Responsive wrapper with sm breakpoint offsets
- `frontend/tsconfig.json` — Removed conflicting NodeNext module/moduleResolution settings
