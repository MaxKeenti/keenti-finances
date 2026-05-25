---
status: accepted
---

# Railway two-service topology with private networking

Production deploys as two Railway services from the same repo — `backend/` and `frontend/`, each with its own Dockerfile and root directory — plus a Railway PostgreSQL plugin. The frontend reaches Quarkus through Railway private networking via `BACKEND_URL`; Quarkus has no public ingress. Railway terminates HTTPS and routes; there is no nginx or other reverse proxy.

Two services match Railway's native monorepo pattern, Dockerfiles give deterministic builds (vs Nixpacks auto-detection), and private networking is what enforces ADR-0002 (Quarkus is internal). Single-service or external reverse-proxy setups were rejected as needless complexity for two well-isolated artifacts.

## Consequences

- Removing private networking (e.g. exposing Quarkus on a public port) would bypass auth — see ADR-0002. The two ADRs are load-bearing for each other.
