---
status: accepted
---

# The app moves, and its addresses keep working

Keenti's Users are moving to a rebuilt app at another address, with the same WorkOS identities.
Bookmarks and public Subscription links shared with people outside the app point here, and a
Subscription's members have no account to find their way back with.

## Decision

This address stays, and stays attached to this service. With `MOVED_TO` set, every request except
`/health` is answered with a redirect to the same page there, and nothing reaches the backend.

- **A record's page goes to that record.** Its id there is worked out from its table and its id
  here, by the rule that carried it across, so no table of pairs has to be kept.
- **A public Subscription link keeps its path and its token,** which works there unchanged.
- **Temporary redirects (307).** Nothing relies on these being cached, and a browser that cached a
  permanent one could not be brought back if the move were taken back, or moved on again later.
- **Reads and writes alike.** A tab left open finds its next change refused by the new address, so
  nothing can reach this database after the move, and it stays as the move left it.
- **One variable, and no deploy of new code.** Setting it is the move, and unsetting it takes it
  back. The code ships inert.

**Considered options:** moving the address to the new service, which could retire this one, but a
Railway domain cannot be got back once deleted, and every public link depends on it; redirecting at
another layer, when Railway has none for a service domain.

**Consequences:** this frontend keeps running to answer old addresses. It sleeps when idle, so the
first request after a quiet spell takes a few seconds. It no longer needs the backend or the
database, which can be retired once the new home has proved itself.
