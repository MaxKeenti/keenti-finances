# S01: Auth & Hexagonal Foundation — UAT

**Milestone:** M001
**Written:** 2026-05-13T20:38:36.816Z

# UAT: S01 — Auth & Hexagonal Foundation

## UAT Type
Integration — requires both Quarkus backend (with PostgreSQL) and SvelteKit frontend running locally.

## Preconditions
- PostgreSQL running at `localhost:5432`, database `keenti_finances` exists
- Quarkus backend started: `cd backend && ./mvnw quarkus:dev` (Flyway runs V1 migration on startup)
- SvelteKit frontend started: `cd frontend && bun run dev`
- Admin user seeded by migration (username: `admin`, password: `Ch@ngeMe2025!` or whatever was seeded)
- Browser: Mobile Safari on iPhone (390px viewport) **and** desktop browser at 1440px

---

## Scenario 1 — Unauthenticated redirect to login

**Steps:**
1. Open a fresh browser session (no cookies).
2. Navigate to `http://localhost:5173/`.

**Expected outcome:**
- Browser redirects to `http://localhost:5173/login`.
- Login page renders with username and password fields and a submit button.
- No app shell (no sidebar, no bottom nav) is visible.

---

## Scenario 2 — Failed login returns error

**Steps:**
1. On the login page, enter `admin` and an incorrect password.
2. Submit the form.

**Expected outcome:**
- Page remains on `/login`.
- An error message is displayed (e.g. "Invalid credentials" or similar).
- No session cookie is set.
- Quarkus log shows a structured failure entry with username and timestamp.

---

## Scenario 3 — Successful login, desktop app shell

**Steps:**
1. Open a desktop browser at 1440px width.
2. Navigate to `http://localhost:5173/login`.
3. Enter correct credentials and submit.

**Expected outcome:**
- Redirect to `http://localhost:5173/`.
- Sidebar is visible on the left (240px wide): app name, nav links (Dashboard, Transactions, Subscriptions, Debts), logout at the bottom.
- No bottom tab bar visible.
- Dashboard area shows a placeholder card.
- Session cookie is present (HTTP-only, SameSite=Lax).
- Quarkus log shows a structured success entry with username and timestamp.

---

## Scenario 4 — Successful login, mobile Safari app shell

**Steps:**
1. Open Mobile Safari on an iPhone (or set viewport to 390px).
2. Navigate to `http://localhost:5173/login`.
3. Enter correct credentials and submit.

**Expected outcome:**
- Redirect to `/`.
- No sidebar visible.
- Bottom tab bar visible at the bottom of the screen with nav icons (Dashboard, Transactions, Subscriptions, Debts) plus logout.
- Dashboard area renders above the tab bar without overlap.

---

## Scenario 5 — Session persists across page reload

**Steps:**
1. After logging in (Scenario 3 or 4), reload the page.

**Expected outcome:**
- User remains on `/` (not redirected to `/login`).
- App shell is still rendered with the correct nav.

---

## Scenario 6 — Logout clears session

**Steps:**
1. While authenticated, click the logout link/button in the sidebar (desktop) or bottom nav (mobile).

**Expected outcome:**
- Redirect to `/login`.
- Session cookie is cleared (no longer present).
- Navigating to `/` again redirects back to `/login`.

---

## Scenario 7 — Direct navigation to protected route while unauthenticated

**Steps:**
1. Clear all cookies (or open incognito).
2. Navigate directly to `http://localhost:5173/transactions` (a future route).

**Expected outcome:**
- Redirect to `/login` (auth guard intercepts any non-exempt path).

---

## Edge Cases

- **Wrong password multiple times**: Each attempt returns an error; no account lockout (single-user app, lockout not in scope for S01).
- **Tampered session cookie**: Manually edit the cookie value in DevTools and reload. Expected: redirect to `/login` (HMAC validation fails).
- **Backend down**: Submit login form with Quarkus not running. Expected: error message on login page (502 from proxy, translated to user-facing failure message).

---

## Not Proven By This UAT

- Database persistence across restarts (proven by Flyway migration running on startup)
- Subscription, transaction, debt, or dashboard features (S02–S07)
- Production deployment or HTTPS (S08)
- Mobile Safari-specific CSS quirks beyond viewport width (requires physical device testing)
- Password change flow (not in scope for M001)
