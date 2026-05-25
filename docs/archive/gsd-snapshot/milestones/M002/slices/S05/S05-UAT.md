# S05: Passkey Auth via WorkOS — UAT

**Milestone:** M002
**Written:** 2026-05-17T23:25:28.528Z

## UAT: Passkey Auth via WorkOS

### Prerequisites
- WorkOS project configured with passkey provider
- Env vars set: WORKOS_API_KEY, WORKOS_CLIENT_ID, WORKOS_COOKIE_PASSWORD (32+ chars), PUBLIC_APP_URL

### Test Cases

**TC1: Unauthenticated redirect to WorkOS**
1. Open app in a fresh private window (no session cookie)
2. Navigate to any protected route (e.g. /)
3. Expected: Browser redirects to WorkOS hosted auth UI

**TC2: Passkey registration and login**
1. Complete passkey registration via WorkOS auth UI
2. Authenticate with the passkey
3. Expected: Browser redirects to /callback, then to /; layout shows user name from session

**TC3: Session persistence**
1. After login, inspect cookies — should see an encrypted session cookie (opaque ciphertext, not a readable JWT)
2. Reload the page
3. Expected: Stays logged in; no redirect to WorkOS

**TC4: Token refresh**
1. Set a very short expiry in WorkOS dashboard (or wait for access token to expire)
2. Make a request to a protected route
3. Expected: Token silently refreshed; user remains logged in

**TC5: Logout**
1. Click logout
2. Expected: Session cookie cleared; browser redirects to /login then immediately to WorkOS (if unauthenticated access attempted)

**TC6: Backend health**
1. Check that /auth backend endpoints are gone (404)
2. Expected: No AuthResource endpoints respond

### Observability
- Server console must log `[workos-auth] redirect`, `[workos-auth] session-create`, `[workos-auth] logout` at appropriate points
- WorkOS dashboard → User Management → Events must show authentication events
