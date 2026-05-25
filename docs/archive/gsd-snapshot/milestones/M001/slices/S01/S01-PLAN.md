# S01: Auth & Hexagonal Foundation

**Goal:** Single-user password authentication with session management on SvelteKit, hexagonal package structure on Quarkus backend with Flyway migrations, SvelteKit-to-Quarkus proxy pattern, and a responsive authenticated app shell. Unauthenticated requests redirect to login.
**Demo:** Log in with password on mobile Safari, see authenticated empty shell; unauthenticated requests redirect to login

## Must-Haves

- POST /api/auth/login with correct credentials returns session cookie
- Navigating to / without session redirects to /login
- After login, / shows authenticated app shell with sidebar/bottom nav
- Layout is usable at 390px (mobile) and 1440px (desktop) widths
- Backend follows hexagonal architecture: domain layer has no framework imports
- Flyway migration creates user table and seeds the single admin user
- Backend compiles: ./mvnw compile
- Frontend type-checks: bun run check

## Proof Level

- This slice proves: integration

## Integration Closure

Upstream: none (first slice). New wiring: SvelteKit proxy to Quarkus via +server.ts catch-all, auth hooks in hooks.server.ts, session cookie round-trip. Remaining for milestone: all feature slices S02-S07 build on this foundation, S08 deploys.

## Verification

- Auth login attempts logged with structured Quarkus logging (username, success/failure, timestamp). SvelteKit hooks log auth redirects. Failed login returns 401 with JSON error body.

## Tasks

- [x] **T01: Scaffold hexagonal backend structure, add dependencies, and create Flyway user migration** `est:1h`
  Set up the hexagonal package structure under com.keenti.finances with domain/application/infrastructure layers. Add Quarkus dependencies (Panache Hibernate ORM, REST Jackson, Hibernate Validator, jbcrypt). Configure application.properties for dev PostgreSQL and Flyway. Create V1__create_user_table.sql migration with id, username, password_hash columns and a seeded bcrypt-hashed admin user. Remove the default GreetingResource and its tests.
  - Files: `backend/pom.xml`, `backend/src/main/resources/application.properties`, `backend/src/main/resources/db/migration/V1__create_user_table.sql`, `backend/src/main/java/org/acme/GreetingResource.java`, `backend/src/test/java/org/acme/GreetingResourceTest.java`, `backend/src/test/java/org/acme/GreetingResourceIT.java`
  - Verify: cd backend && ./mvnw compile -q && echo 'COMPILE OK' && test -f src/main/resources/db/migration/V1__create_user_table.sql && grep -q 'quarkus-hibernate-orm-panache' pom.xml && ! test -f src/main/java/org/acme/GreetingResource.java && echo 'ALL CHECKS PASSED'

- [x] **T02: Implement auth domain model, ports, application service, and REST login endpoint** `est:1h30m`
  Build the auth vertical through all hexagonal layers. Domain: User model (POJO with id, username, passwordHash). Ports: AuthUseCase (inbound), UserRepository and PasswordHasher (outbound). Application: AuthService implementing AuthUseCase with login(username, password) returning Optional<User>. Infrastructure: UserEntity (Panache entity), PanacheUserRepository implementing UserRepository, BcryptPasswordHasher implementing PasswordHasher, AuthResource (JAX-RS POST /api/auth/login accepting JSON {username, password}, returning 200 with user info or 401).
  - Files: `backend/src/main/java/com/keenti/finances/domain/model/User.java`, `backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java`, `backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java`, `backend/src/main/java/com/keenti/finances/application/service/AuthService.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java`, `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java`
  - Verify: cd backend && ./mvnw compile -q && echo 'COMPILE OK' && grep -q 'BCrypt.checkpw' src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java && grep -q '@Path' src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java && echo 'ALL CHECKS PASSED'

- [x] **T03: Wire SvelteKit proxy to Quarkus, implement session management and login page with auth guard** `est:2h`
  Set up SvelteKit as the auth and proxy layer. Create a catch-all +server.ts under /api/[...path] that forwards requests to the Quarkus backend (http://localhost:8080). Implement session management using signed HTTP-only cookies (SvelteKit cookies API with HMAC-SHA256 signing via Node crypto). Create the login page with a superforms-validated form that POSTs to a +page.server.ts action, which calls the Quarkus /api/auth/login, and on success sets the session cookie and redirects to /. Add hooks.server.ts handle hook that checks for valid session cookie on all routes except /login and /api/auth/login, redirecting to /login if missing/invalid. Update app.d.ts with session types in App.Locals. Install zod dependency.
  - Files: `frontend/src/routes/api/[...path]/+server.ts`, `frontend/src/lib/server/session.ts`, `frontend/src/app.d.ts`, `frontend/src/hooks.server.ts`, `frontend/src/routes/login/+page.server.ts`, `frontend/src/routes/login/+page.svelte`, `frontend/src/routes/+layout.server.ts`, `frontend/package.json`
  - Verify: cd frontend && bun run check && echo 'TYPE CHECK OK' && test -f src/hooks.server.ts && test -f src/routes/api/\[...path\]/+server.ts && test -f src/routes/login/+page.svelte && grep -q 'SESSION_SECRET' src/lib/server/session.ts && echo 'ALL CHECKS PASSED'

- [x] **T04: Build responsive authenticated app shell with sidebar navigation and logout** `est:1h`
  Create the authenticated app shell layout visible after login. Desktop (≥640px): sidebar navigation with app name, nav links (Dashboard, Transactions, Subscriptions, Debts), and logout button at the bottom. Mobile (<640px): bottom tab bar with the same nav items as icons. The dashboard route (/) shows a placeholder card. Add logout action that clears the session cookie and redirects to /login. Update the root +layout.svelte to conditionally render the app shell when authenticated vs. bare layout for login.
  - Files: `frontend/src/lib/components/app-shell/sidebar.svelte`, `frontend/src/lib/components/app-shell/bottom-nav.svelte`, `frontend/src/lib/components/app-shell/app-shell.svelte`, `frontend/src/routes/+layout.svelte`, `frontend/src/routes/+page.svelte`, `frontend/src/routes/logout/+page.server.ts`
  - Verify: cd frontend && bun run check && echo 'TYPE CHECK OK' && test -f src/lib/components/app-shell/app-shell.svelte && test -f src/routes/logout/+page.server.ts && echo 'ALL CHECKS PASSED'

## Files Likely Touched

- backend/pom.xml
- backend/src/main/resources/application.properties
- backend/src/main/resources/db/migration/V1__create_user_table.sql
- backend/src/main/java/org/acme/GreetingResource.java
- backend/src/test/java/org/acme/GreetingResourceTest.java
- backend/src/test/java/org/acme/GreetingResourceIT.java
- backend/src/main/java/com/keenti/finances/domain/model/User.java
- backend/src/main/java/com/keenti/finances/domain/port/in/AuthUseCase.java
- backend/src/main/java/com/keenti/finances/domain/port/out/UserRepository.java
- backend/src/main/java/com/keenti/finances/domain/port/out/PasswordHasher.java
- backend/src/main/java/com/keenti/finances/application/service/AuthService.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/UserEntity.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheUserRepository.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/security/BcryptPasswordHasher.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AuthResource.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginRequest.java
- backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/LoginResponse.java
- frontend/src/routes/api/[...path]/+server.ts
- frontend/src/lib/server/session.ts
- frontend/src/app.d.ts
- frontend/src/hooks.server.ts
- frontend/src/routes/login/+page.server.ts
- frontend/src/routes/login/+page.svelte
- frontend/src/routes/+layout.server.ts
- frontend/package.json
- frontend/src/lib/components/app-shell/sidebar.svelte
- frontend/src/lib/components/app-shell/bottom-nav.svelte
- frontend/src/lib/components/app-shell/app-shell.svelte
- frontend/src/routes/+layout.svelte
- frontend/src/routes/+page.svelte
- frontend/src/routes/logout/+page.server.ts
