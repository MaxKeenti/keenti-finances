---
estimated_steps: 6
estimated_files: 1
skills_used: []
---

# T04: SvelteKit handleFetch hook for user identity header injection

Why: Backend now requires X-WorkOS-User-Id on all non-public requests. SvelteKit load/action functions make direct backend calls, so the header must be injected centrally via handleFetch hook.

Do:
1. Add handleFetch export to frontend/src/hooks.server.ts: if request URL starts with BACKEND_URL and event.locals.session exists, clone request and add X-WorkOS-User-Id header with event.locals.session.user.id.
2. Verify proxy route works correctly (uses fetch which goes through handleFetch in server context).
3. Verify public paths work: /public/subscription/[token] fetches without session; handleFetch skips header when session is null.

Done when: Frontend builds; authenticated requests include X-WorkOS-User-Id; public subscription page works without the header.

## Inputs

- `frontend/src/hooks.server.ts`
- `frontend/src/routes/api/[...path]/+server.ts`

## Expected Output

- `frontend/src/hooks.server.ts`

## Verification

cd frontend && npm run build
