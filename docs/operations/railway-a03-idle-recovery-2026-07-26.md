# Railway A03 idle-recovery evidence — 2026-07-26

## Scope

- Project: `keenti-finances` (`4ed3c804-4f91-4b43-a912-1ba7de9accc0`)
- Environment: `production` (`a08caa22-994e-4dbd-92c2-7cf5eaa698e9`)
- PostgreSQL: `Postgres` (`13ae5162-fb6a-4d64-a3c3-e9758b5a8011`)
- Backend: `backend` (`8949110a-ed94-4976-9ec5-cfc229ff889c`)
- Frontend: `frontend` (`55a08c17-2836-4615-a3e7-ab492c85e450`)

No variables or secret values were read or recorded.

## Configuration mutation

The preflight matched every expected project, environment, and service identifier.
Before the mutation, all three services had `deploy.sleepApplication=true`.

Only PostgreSQL was changed. Railway accepted `sleepApplication=false`; its live
environment configuration now omits the PostgreSQL sleep override, which is the
platform's effective false/default state. Backend and frontend remain explicitly
`true`.

| Service | Before | After |
| --- | ---: | ---: |
| PostgreSQL | `true` | `false` (default/override omitted) |
| Backend | `true` | `true` |
| Frontend | `true` | `true` |

## Probe method

Each probe uses an HTTP GET through the public frontend to the public Subscription
View route with the nonexistent UUID
`00000000-0000-0000-0000-000000000000`. This is a harmless database-backed read.
The expected recovered response is `404`; a `500` or `502` is a user-visible
failure. The first result is recorded before any retry.

## Results

| Attempt | UTC timestamp | Context | First HTTP result | Latency | Targeted stale-connection logs |
| ---: | --- | --- | ---: | ---: | --- |
| 1 | 2026-07-26T23:25:12Z | Frontend, backend, and PostgreSQL sleeping | `502` | 1,657 ms | None |

The 20-recovery acceptance run stopped after attempt 1 because the completion
signal ("all 20 first attempts avoid user-visible 500s") was already impossible
to satisfy. Continuing with retries would obscure the failed first-attempt
behavior.

One non-soak diagnostic request at `2026-07-26T23:25:55Z` returned the expected
`404` in 658 ms after all services had recovered.

## Log correlation

- Frontend started at approximately `23:25:14Z`, listened on port 3000, and
  logged `backend unreachable` for the first request.
- Backend started at approximately `23:25:15Z`; Quarkus reported ready around
  `23:25:19Z`.
- PostgreSQL started at approximately `23:25:16Z` and was ready for connections
  around `23:25:17Z`.
- Backend startup successfully validated all 16 Flyway migrations and reported
  schema version 16 with no migration required.
- Bounded backend logs contained no `SQLState 08006`, EOF, closed-connection, or
  datasource-acquisition error for the probe.

## Decision

Keep PostgreSQL sleep disabled. The observed failure is a coordinated application
cold-start race rather than a stale PostgreSQL connection.

A03 does not authorize changing backend sleep. The next availability decision
should either:

1. disable backend sleep, or
2. make the frontend's first backend read tolerate the backend wake interval
   without returning a user-visible error.

Until one of those options is implemented, the idle-recovery reliability target
is not met.
