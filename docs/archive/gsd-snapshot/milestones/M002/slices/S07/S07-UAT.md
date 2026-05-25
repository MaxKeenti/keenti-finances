# S07: Railway Deployment & Production Verification — UAT

**Milestone:** M002
**Written:** 2026-05-18T08:47:10.027Z

## UAT: S07 — Railway Deployment & Production Verification

### Pre-deploy Build Verification
- [x] Backend Maven build succeeds (exit 0)
- [x] All 12 integration tests pass (0 failures)
- [x] Frontend Vite build succeeds (exit 0)
- [x] svelte-check reports 0 application errors

### Flyway Migrations
- [x] V7__add_color_to_category.sql present and valid
- [x] V8__add_owner_participates_and_subscription_id.sql present and valid
- [x] V9__make_password_hash_nullable.sql present and valid

### Deployment Checklist
- [x] DEPLOY-M002.md exists at repository root
- [x] Frontend env vars section covers BACKEND_URL, WORKOS_API_KEY, WORKOS_CLIENT_ID, WORKOS_COOKIE_PASSWORD, ORIGIN
- [x] Backend env vars section notes no changes from M001
- [x] WorkOS dashboard config section covers redirect URI and AuthKit setup
- [x] Post-deploy verification checklist included
- [x] Rollback notes included

### Human-Required (Post-Deploy)
- [ ] Configure Railway env vars per DEPLOY-M002.md
- [ ] Register redirect URI in WorkOS dashboard
- [ ] Deploy and verify Flyway migrations apply (V7, V8, V9)
- [ ] Smoke test: passkey registration and login
- [ ] Smoke test: theme detection (light/dark)
- [ ] Smoke test: manual billing trigger
- [ ] Smoke test: dock navigation on mobile and desktop
