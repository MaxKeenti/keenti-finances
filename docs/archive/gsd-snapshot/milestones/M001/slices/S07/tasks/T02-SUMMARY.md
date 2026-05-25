---
id: T02
parent: S07
milestone: M001
key_files:
  - frontend/src/hooks.server.ts
  - frontend/src/routes/public/subscription/[token]/+page.server.ts
  - frontend/src/routes/public/subscription/[token]/+page.svelte
key_decisions:
  - Used path prefix matching ('/public' in PUBLIC_PATHS) rather than per-route bypasses so all future /public/* routes automatically bypass auth without further hooks.server.ts changes
  - Exported PublicSubscriptionData type from +page.server.ts to give +page.svelte full type safety via $types import
  - Flattened member×payment rows and grouped by billingDate in $derived for clean read-only table without needing a separate memberId lookup function
duration: 
verification_result: passed
completed_at: 2026-05-14T20:32:52.793Z
blocker_discovered: false
---

# T02: Built SvelteKit /public/subscription/[token] read-only page with auth bypass — renders subscription info and member payment status without login

**Built SvelteKit /public/subscription/[token] read-only page with auth bypass — renders subscription info and member payment status without login**

## What Happened

Added '/public' to PUBLIC_PATHS in hooks.server.ts so the entire /public/* prefix bypasses authentication. Created +page.server.ts that fetches from the backend's /api/public/subscriptions/{token} endpoint, throws SvelteKit error(404) on 404 responses and error(502) on other failures. Created +page.svelte that renders a full-page layout (no sidebar/nav) with: a header card showing subscription name, cost, billing cycle badge, next billing date, and a "read-only shared view" note; a members card listing each member and share amount; a payment records section that flattens member×payment rows and groups them by billing date (descending) with PENDING/PAID badges. No action buttons or forms — purely read-only. The page works without authentication thanks to the PUBLIC_PATHS change. Invalid tokens are handled by the 404 from the backend propagating as a SvelteKit 404 error page.

## Verification

Ran grep to confirm '/public' is in PUBLIC_PATHS. Confirmed both route files exist at the correct path. Ran svelte-check and confirmed zero errors in the new public route files (pre-existing baseline errors in session.ts, login, categories pages are unrelated and unchanged).

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `grep -q "'/public'" frontend/src/hooks.server.ts && echo PASS` | 0 | PASS — /public added to PUBLIC_PATHS | 50ms |
| 2 | `test -f frontend/src/routes/public/subscription/[token]/+page.server.ts && echo PASS` | 0 | PASS — +page.server.ts exists | 30ms |
| 3 | `test -f frontend/src/routes/public/subscription/[token]/+page.svelte && echo PASS` | 0 | PASS — +page.svelte exists | 30ms |
| 4 | `bun run check 2>&1 | grep -i error | grep public/subscription` | 0 | PASS — zero errors in new public route files | 45000ms |

## Deviations

Step 4 in the plan (optional +layout.svelte for minimal layout without sidebar/nav) was implemented inline in +page.svelte instead — the page wraps its content in a full-screen `min-h-screen bg-background py-10 px-4` container, which achieves the same visual isolation without an extra layout file.

## Known Issues

none

## Files Created/Modified

- `frontend/src/hooks.server.ts`
- `frontend/src/routes/public/subscription/[token]/+page.server.ts`
- `frontend/src/routes/public/subscription/[token]/+page.svelte`
