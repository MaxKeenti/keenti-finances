---
status: accepted
---

# SvelteKit owns authentication; Quarkus is a trusted internal API

The SvelteKit server is the only thing that talks to an identity provider, holds session state, and authorizes requests. Quarkus does not validate tokens or run an auth filter — it trusts that any request it receives has already been authenticated upstream and uses an `X-WorkOS-User-Id` header for tenancy (see ADR-0013).

This avoids double-auth, removes CORS, and keeps Quarkus as a pure API. It depends on Quarkus being unreachable from the public internet — see ADR-0003 (proxy) and ADR-0007 (Railway private networking) for how that's enforced.

## Consequences

- A misconfiguration that exposes Quarkus directly would bypass auth entirely. The two guard rails (proxy-only access + Railway private networking) must both hold.
- Internal jobs (`SubscriptionBillingScheduler`, `PublicSubscriptionResource`) intentionally run without a User scope and bypass the request filter that enables `userScope` — see ADR-0012.
