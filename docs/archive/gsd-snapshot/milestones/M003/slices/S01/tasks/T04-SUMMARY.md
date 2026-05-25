---
id: T04
parent: S01
milestone: M003
key_files:
  - frontend/src/hooks.server.ts
key_decisions:
  - handleFetch clones the Request with new Headers rather than mutating — Request is immutable once constructed
  - BACKEND_URL constant scoped at module level, matching the proxy route's own fallback pattern
  - Session-null guard means public subscription pages fetch the backend without X-WorkOS-User-Id, as required
duration: 
verification_result: passed
completed_at: 2026-05-23T18:44:49.488Z
blocker_discovered: false
---

# T04: Added handleFetch hook to centrally inject X-WorkOS-User-Id header on all backend requests when a session exists.

**Added handleFetch hook to centrally inject X-WorkOS-User-Id header on all backend requests when a session exists.**

## What Happened

Added `handleFetch` export to `frontend/src/hooks.server.ts`. The hook intercepts every `fetch` call made in server-side load functions and actions (including those in the proxy route). When the request URL starts with `BACKEND_URL` and `event.locals.session` is non-null, it clones the request with a new `Headers` object containing `X-WorkOS-User-Id` set to `event.locals.session.user.id`. When session is null (public paths like `/public/subscription/[token]`), the request passes through unmodified, preserving unauthenticated access to backend public endpoints. The `BACKEND_URL` constant is derived from `process.env.BACKEND_URL` with the same `http://localhost:8080` fallback used by the proxy route, ensuring consistent targeting.

## Verification

Ran `bun install --frozen-lockfile && bun run build` in `frontend/`. Build exited 0 with no type errors. The `HandleFetch` type from `@sveltejs/kit` was imported alongside `Handle` and the hook compiled cleanly. Circular dependency warnings in output are pre-existing from third-party node_modules (typebox, zod-v3-to-json-schema, @internationalized/date) and are unrelated to this change.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun install --frozen-lockfile && bun run build` | 0 | pass | 11456ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/hooks.server.ts`
