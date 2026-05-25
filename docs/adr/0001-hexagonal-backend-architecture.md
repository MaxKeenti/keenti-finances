---
status: accepted
---

# Hexagonal architecture on the Quarkus backend

The backend is structured as `domain → application → infrastructure` with ports and adapters: domain models are framework-free POJOs, Panache entities and REST resources live as adapters in `infrastructure/`, and use-case orchestration sits in `application/service/`.

This keeps domain logic testable and framework-independent — Panache, JAX-RS, and bcrypt are swappable adapters rather than load-bearing dependencies. The cost is some duplication between domain POJOs and Panache entities, accepted as the price of the boundary.

## Considered options

- **Standard layered Quarkus (REST → Service → Panache entity directly):** rejected because domain logic gets pulled into framework-coupled classes and unit tests need a Quarkus context.
