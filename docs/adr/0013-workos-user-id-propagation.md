---
status: accepted
---

# User identity propagates from SvelteKit to Quarkus via `X-WorkOS-User-Id` header

The SvelteKit `handleFetch` hook injects `X-WorkOS-User-Id` on every backend-bound request (proxy route and `page.server.ts` direct calls alike). On the Quarkus side, the request filter from ADR-0012 reads the header, looks up `app_user` by `workos_id`, and just-in-time provisions a new `app_user` row on first sight. The resolved local `userId` lives in a `@RequestScoped UserContext` bean for the rest of the request.

`workos_id` is the external identity; the local numeric `app_user.id` is the FK used everywhere in the schema and is the stable handle for per-User preferences. Validating a WorkOS JWT inside Quarkus was rejected as unnecessary double-auth — see ADR-0002. Injecting the header in `handleFetch` rather than in each `page.server.ts` is a central enforcement point that page-by-page edits cannot quietly skip.
