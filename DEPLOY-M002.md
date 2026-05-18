# M002 Deployment Checklist

Incremental deployment guide for M002 features. Assumes M001 is already deployed per `DEPLOY.md`.

---

## 1. Frontend Environment Variables (New for M002)

| Variable | Source | Notes |
|---|---|---|
| `WORKOS_API_KEY` | WorkOS Dashboard → API Keys | Starts with `sk_...` — server-side only, never expose to client |
| `WORKOS_CLIENT_ID` | WorkOS Dashboard → API Keys | Starts with `client_...` |
| `WORKOS_COOKIE_PASSWORD` | Generate: `openssl rand -hex 32` | Must be ≥ 32 characters. Signs the encrypted session cookie |
| `ORIGIN` | Your frontend public URL | **⚠️ CRITICAL** — e.g. `https://your-app.up.railway.app`. SvelteKit uses this to build the OAuth callback URL. Without it, WorkOS returns a redirect URI mismatch error and auth fails silently. No trailing slash. |

### Existing variables (unchanged from M001)

| Variable | Notes |
|---|---|
| `BACKEND_URL` | Railway private network URL — no change |
| `NODE_ENV` | `production` — no change |
| `PORT` | `3000` — no change |

> **M002 removes `SESSION_SECRET`** — session management moved from custom cookie signing to WorkOS encrypted sessions via `WORKOS_COOKIE_PASSWORD`.

---

## 2. Backend Environment Variables (No Changes)

| Variable | Notes |
|---|---|
| `DATABASE_URL` | Already set from M001 |
| `DATABASE_USER` | Already set from M001 |
| `DATABASE_PASSWORD` | Already set from M001 |

No new backend env vars. The backend is now a trusted internal API — authentication is handled entirely by the SvelteKit frontend via WorkOS.

---

## 3. WorkOS Dashboard Configuration

1. **Create a WorkOS account** at [workos.com](https://workos.com) if you haven't already.

2. **Get API credentials:**
   - Navigate to **API Keys** in the WorkOS dashboard.
   - Copy the **API Key** (`sk_...`) → set as `WORKOS_API_KEY`.
   - Copy the **Client ID** (`client_...`) → set as `WORKOS_CLIENT_ID`.

3. **Register the redirect URI:**
   - Go to **Redirects** in the WorkOS dashboard.
   - Add: `https://<your-frontend-domain>/callback`
   - This must exactly match what SvelteKit generates from the `ORIGIN` env var + `/callback`.

4. **Enable AuthKit with passkeys:**
   - Go to **Authentication** → **AuthKit**.
   - Enable **Passkeys** as an authentication method.
   - **Note:** Passkeys are bound to the domain they're registered on. If you change your frontend domain later, existing passkeys will stop working and users will need to re-register.

---

## 4. Flyway Migrations (Automatic)

These migrations run automatically on backend startup. No manual SQL execution needed.

| Migration | Description | Risk |
|---|---|---|
| `V7__add_color_to_category.sql` | Adds nullable `color` column to `category` table | Additive — no data loss |
| `V8__add_owner_participates_and_subscription_id.sql` | Adds `owner_participates` boolean (default `true`) to `subscription`; adds nullable `subscription_id` FK to `transaction` | Additive — existing subscriptions default to owner participating |
| `V9__make_password_hash_nullable.sql` | Makes `password_hash` nullable on `app_user` | Relaxes constraint — passkey-only users have no password hash |

All three are additive `ALTER TABLE` statements. They do not modify or delete existing data.

---

## 5. Post-Deploy Verification Checklist

After deploying both services:

- [ x ] **Backend health:** `GET https://<backend-public-url>/q/health` returns `{"status":"UP"}`
- [ x ] **Frontend health:** `GET https://<frontend-url>/health` returns `{"status":"UP"}`
- [ x ] **Auth flow:** Navigate to the app → redirected to WorkOS AuthKit → sign in or register passkey → redirected back to dashboard
- [ x ] **Theme detection:** App respects system light/dark preference on first load (no flash)
- [ x ] **Dock navigation:** Bottom dock visible on desktop; mobile shows 3 pinned items + overflow menu
- [ x ] **Categories:** Category badges show assigned colors
- [ x ] **Subscriptions:** Shared subscriptions show member list and public payment link
- [ ] **Billing trigger:** In a shared subscription, the "Generate Bills" action creates payment records for members
- [ ] **Debt tracking:** Create a debt, record a payment, verify balance updates

---

## 6. Rollback

All M002 changes are safe to roll back:

- **Frontend:** Redeploy the previous M001 image. Remove the four new env vars (`WORKOS_API_KEY`, `WORKOS_CLIENT_ID`, `WORKOS_COOKIE_PASSWORD`, `ORIGIN`). Re-add `SESSION_SECRET`.
- **Backend:** Redeploy the previous M001 image. Flyway won't re-run old migrations.
- **Database:** V7-V9 are all additive (nullable columns, relaxed constraints). The M001 backend image will simply ignore the extra columns. No data is lost.

If you need to fully revert the schema, run manually:
```sql
ALTER TABLE category DROP COLUMN IF EXISTS color;
ALTER TABLE transaction DROP COLUMN IF EXISTS subscription_id;
ALTER TABLE subscription DROP COLUMN IF EXISTS owner_participates;
ALTER TABLE app_user ALTER COLUMN password_hash SET NOT NULL;
```

> **Warning:** The `SET NOT NULL` on `password_hash` will fail if any passkey-only users exist (they have null password hashes). Delete or update those rows first.
