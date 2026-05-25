---
status: accepted
---

# Multi-user isolation via monolithic query-scoped filters, not microservices

Tenancy is enforced inside a single monolithic Quarkus app by attaching `user_id = :currentUser` to every domain query. PostgreSQL MVCC handles concurrency for the expected scale (~30 Users). Hexagonal architecture funnels all data access through repository ports, so user-scope filtering at that layer gives tenant isolation without distributed transactions, inter-service auth, or operational overhead.

Splitting into per-tenant services or schemas was rejected: the same `WHERE user_id = ?` clause is needed regardless of topology, and microservices would add deployment, networking, and consistency cost for no tenancy benefit at this scale. See ADR-0012 for the mechanism, ADR-0013 for how `currentUser` is resolved, and ADR-0014 for which tables carry `user_id`.
