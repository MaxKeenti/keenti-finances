---
status: accepted
---

# All browser↔Quarkus traffic flows through the SvelteKit proxy

The browser never talks to Quarkus directly. A catch-all `+server.ts` at `/api/[...path]` forwards every HTTP method to Quarkus via `BACKEND_URL`. `page.server.ts` calls go through the same SvelteKit `handleFetch` hook so they pick up the same headers.

Single origin, no CORS, all auth concerns live in the SvelteKit layer. The catch-all pattern means new API routes need no per-endpoint proxy file.

## Consequences

- All server-side fetches inherit `X-WorkOS-User-Id` injection via `handleFetch` (see ADR-0013).
- Any future requirement for the browser to call Quarkus directly (e.g. streaming, websockets) would break this and require a redesign of the auth flow.
