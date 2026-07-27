# Railway A03 idle-recovery evidence — 2026-07-26

## Scope

- Project: `keenti-finances` (`4ed3c804-4f91-4b43-a912-1ba7de9accc0`)
- Environment: `production` (`a08caa22-994e-4dbd-92c2-7cf5eaa698e9`)
- PostgreSQL: `Postgres` (`13ae5162-fb6a-4d64-a3c3-e9758b5a8011`)
- Backend: `backend` (`8949110a-ed94-4976-9ec5-cfc229ff889c`)
- Frontend: `frontend` (`55a08c17-2836-4615-a3e7-ab492c85e450`)

No variables or secret values were read or recorded.

## Final sleep policy

The preflight matched every expected project, environment, and service identifier.
All three services initially had `deploy.sleepApplication=true`.

An earlier API call accepted a request to disable PostgreSQL sleep, but the
subsequent deployment manifest and observed service state still reported
PostgreSQL as sleeping. No claim is made that this mutation persisted.

The chosen cost policy is to retain Railway Serverless behavior for frontend,
backend, and PostgreSQL. A production monitor confirmed all three services in
`SLEEPING` state before the post-mitigation probe.

## Wake-aware retry

Commit `36190b2` adds one centralized SvelteKit server retry policy:

- only `GET` and `HEAD` requests are eligible;
- network failures and HTTP `502`, `503`, and `504` are transient;
- seven delays provide a total wake budget of 15.75 seconds;
- `POST`, `PUT`, `PATCH`, and `DELETE` remain single-attempt so ambiguous
  failures cannot duplicate financial writes;
- both SvelteKit server loads and the browser API proxy use the policy.

Five focused tests cover recovery, transient gateway responses, normal `404`
responses, write safety, and retry-budget exhaustion. GitHub Actions run
`30225606521` passed the frontend tests/check/build and backend verification.

## Probe method

Each probe uses an HTTP GET through the public frontend to the public Subscription
View route with the nonexistent UUID
`00000000-0000-0000-0000-000000000000`. This is a harmless database-backed read.
The expected recovered response is `404`; a `500` or `502` is a user-visible
failure. Each external first result is recorded without a manual retry. The
application's internal safe-read retry is the mitigation being measured.

## Results

| Cycle | UTC timestamp | Context | First HTTP result | Latency | Targeted stale-connection logs |
| --- | --- | --- | ---: | ---: | --- |
| Baseline 1 | 2026-07-26T23:25:12Z | All three services sleeping; no wake retry | `502` | 1,657 ms | None |
| Retry soak 1 | 2026-07-26T23:55:10Z | All three services sleeping; commit `36190b2` deployed | `404` | 8,837 ms | None |
| Retry soak 2 | 2026-07-27T02:06:55Z | All three services sleeping; commit `36190b2` deployed | `404` | 14,193 ms | None |

The unmitigated run stopped after its first failure. The retry strategy starts a
new 20-recovery acceptance series and is currently 2/20 successful.

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

For retry-soak cycle 1:

- Frontend started at `23:55:11Z` and listened around `23:55:15Z`.
- Backend started at `23:55:13Z`; Quarkus reported ready around `23:55:16Z`.
- PostgreSQL started at `23:55:14Z` and accepted connections after automatic
  recovery.
- The backend validated all 16 migrations and served the harmless lookup around
  `23:55:19Z`.
- The first external request stayed open through that sequence and returned the
  expected `404`.

For retry-soak cycle 2:

- Frontend listened at `02:06:57Z`.
- PostgreSQL completed automatic recovery and accepted connections at
  approximately `02:07:05Z`.
- The backend validated all 16 migrations, confirmed schema version 16, and
  started at approximately `02:07:08Z`.
- The harmless public lookup reached the backend at approximately `02:07:09Z`;
  all three Railway services then reported `SUCCESS`.
- A targeted backend log query found no `SQLState 08006`, EOF, closed-connection,
  JDBC acquisition, or acquisition-timeout signature.

## Decision

Keep Railway Serverless enabled for all three services to minimize idle RAM
cost. The bounded safe-read retry successfully absorbed one complete
frontend/backend/PostgreSQL cold start without replaying writes.

Continue the acceptance series until 20 independent sleep-to-wake cycles have
passed. Revisit the sleep policy only if a first request exceeds the retry
budget, returns a user-visible 5xx, or produces a targeted datasource error.
