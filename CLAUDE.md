# Keenti Finances

## Agent skills

### Issue tracker

GitHub Issues in `MaxKeenti/keenti-finances`, managed via the `gh` CLI. See `docs/agents/issue-tracker.md`.

### Triage labels

Default canonical labels: `needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`. See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: `CONTEXT.md` at the repo root is the domain glossary; `docs/adr/` holds architectural decisions. See `docs/agents/domain.md`.

### Database migrations

Flyway `Vnn__*.sql` files are append-only once deployed. Never edit an applied migration — ship a new one. See `docs/agents/migrations.md` for the failure mode and remediation.
