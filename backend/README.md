# Keenti Finances — Backend

Quarkus (Java 21) API for Keenti Finances. It is a **trusted internal service**: it has no authentication of its own and is only reached through the SvelteKit frontend's `/api/*` proxy, which injects `X-WorkOS-User-Id` (ADR-0003, ADR-0013). Never give it public ingress.

Standards for working in this code live in [`AI_RULES.md`](../AI_RULES.md); domain language in [`CONTEXT.md`](../CONTEXT.md).

## Layout

Hexagonal architecture (ADR-0001), under `src/main/java/com/keenti/finances/`:

| Package | Holds |
|---|---|
| `domain/model` | Framework-free domain objects and pure calculators |
| `domain/port/in` | Use-case interfaces (`*UseCase`) |
| `domain/port/out` | Repository and provider interfaces |
| `application/service` | Use-case implementations |
| `infrastructure/adapter/in/rest` | JAX-RS resources and request/response records |
| `infrastructure/adapter/out/persistence` | Panache `*Entity` classes and `Panache*Repository` adapters |

Schema changes are Flyway migrations in `src/main/resources/db/migration/`. They are **append-only** once deployed — read [`docs/agents/migrations.md`](../docs/agents/migrations.md) before adding one.

Per-User isolation and soft delete are Hibernate filters on root entities (ADR-0011, 0012, 0014, 0018). Native SQL bypasses them and must filter by `user_id` itself.

## Running locally

Dev mode expects PostgreSQL on `localhost:5432`, database `keenti_finances`, user and password `keenti` (see `src/main/resources/application.properties`). Flyway migrates it at startup.

```shell
./mvnw quarkus:dev
```

The API is on <http://localhost:8080>, the Dev UI on <http://localhost:8080/q/dev/>, and health on `/q/health`.

Requests need an `X-WorkOS-User-Id` header to resolve a User; the frontend proxy normally sets it.

## Testing

```shell
./mvnw verify
```

Tests are `@QuarkusTest`s against a throwaway `postgres:18-alpine` started by Quarkus Dev Services, so **Docker must be running**. Every test boot applies all migrations, so a broken migration fails the suite.

CI runs with `TZ=America/Mexico_City`. Date-sensitive tests must pass in that zone as well as UTC:

```shell
TZ=America/Mexico_City ./mvnw verify
```

## Deployment

Built from `Dockerfile` on Railway, with production settings from `%prod.` properties and the `DATABASE_URL`, `DATABASE_USER`, and `DATABASE_PASSWORD` variables. See [`DEPLOY.md`](../DEPLOY.md).
