---
status: accepted
---

# Subscription billing is a daily idempotent date-check scheduler

A Quarkus `@Scheduled` job runs once a day and generates Payment Records for every Subscription whose next billing date falls within the next 7 days, *if* a matching Payment Record does not already exist. It checks dates against the database rather than time-since-last-run, so missed runs, restarts, or manual triggers can never duplicate records.

This gives Members visible 7-day lead time on the Public Subscription View, lets unpaid periods accumulate visibly, and makes the scheduler restart-safe. A manual `POST` trigger reuses the same idempotent path so the user can force a refresh.

## Consequences

- The scheduler intentionally bypasses the userScope filter (no HTTP request context — see ADR-0012). It must therefore set `user_id` explicitly on every Payment Record it writes.
