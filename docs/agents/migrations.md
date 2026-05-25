# Flyway Migrations

Rules for editing `backend/src/main/resources/db/migration/V*.sql`.

## Migrations are append-only

Once a migration file is committed to a branch that has ever been deployed (or merged into a deployed branch), **the file's contents are immutable forever**. This includes whitespace, formatting, comments, and constraint names.

Flyway records a checksum of each migration in the `flyway_schema_history` table when it applies the migration. On every subsequent boot it re-hashes the on-disk file and compares. A mismatch — even a single space — refuses to start the app. The error looks like:

```
FlywayValidateException: Validate failed: Migrations have failed validation
Migration checksum mismatch for migration version N
-> Applied to database : <prod checksum>
-> Resolved locally    : <new checksum>
```

If you need to change anything an applied migration did, ship a **new** migration `V{n+1}__fix_thing.sql`. Do not retouch the original.

## Acceptable edits to applied migrations

None. Not formatting. Not comments. Not "harmless" renames. The validation hash doesn't know "harmless."

## If you discover a divergence anyway

This happened once (M003, V10 — see Railway logs `2026-05-25 23:08:51`). Two versions of the same `Vnn__*.sql` ended up in git history because an auto-commit flow rewrote a file that had already been deployed. The remediation is environment-specific:

1. Confirm the prod DB's actual schema state with `psql` (e.g. `SELECT conname FROM pg_constraint WHERE conrelid = '<table>'::regclass`) — don't assume which version of the file ran there.
2. Decide convergence direction: usually keep the on-disk version as the source of truth and update prod to match it.
3. Run one-off SQL in prod to (a) update `flyway_schema_history.checksum` for the affected version *and* (b) make any schema renames so later migrations referring to the new names succeed.
4. Restart. Validate that subsequent migrations apply cleanly.

The fix is operational, not a code change. Do **not** commit a "fix" to the diverged migration — that just moves the problem to dev environments whose history table records the now-different checksum.

## Numbering

Next free version = highest `Vnn__*.sql` in `backend/src/main/resources/db/migration/` plus 1. Don't skip numbers, don't reuse them. If two PRs both add `Vnn__*.sql` and one merges first, rebase the other to `V{n+1}` before re-pushing.

## Test footprint

Quarkus's `@QuarkusTest` boots a real DB and runs all migrations on it. New `Vnn` migrations are exercised automatically by every existing test — there is no need to write a "does this migration work" test. If your migration breaks, the suite breaks loudly. Conversely, an `@QuarkusTest` that boots clean is strong evidence the migration is well-formed.
