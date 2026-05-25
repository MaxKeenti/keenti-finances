# GSD Snapshot Archive

Frozen snapshot of the project's `.gsd/` planning artifacts at the point we migrated to the Matt Pocock skills documentation system (CONTEXT.md + docs/adr/). **Read-only history. Do not edit.**

- **Captured:** 2026-05-25
- **Source:** `~/.gsd/projects/79f524178d77/` (a symlink target referenced from this repo's `.gsd`)
- **Why archived:** The GSD framework produced rich historical reasoning (decision rationale, milestone summaries, slice plans, retrospectives) that has no equivalent in the new system. The live docs (CONTEXT.md, docs/adr/, docs/LEARNINGS.md) carry forward only what was still load-bearing. This archive preserves everything else for forensic lookup.

## Where the substance went

| Source here | Lives in active docs as |
|---|---|
| `DECISIONS.md` (36 rows D001–D036) | 15 ADRs in `docs/adr/` (see ADR table in those files) |
| `milestones/M001/M001-LEARNINGS.md` | Promoted (lightly edited) to `docs/LEARNINGS.md` |
| Domain language scattered through `PROJECT.md` and `M00X-CONTEXT.md` | `CONTEXT.md` at the repo root |
| `REQUIREMENTS.md` | Not migrated — feature/capability ledger has no new-system equivalent. Frozen here. |
| `milestones/M00X/slices/SNN/*` | Not migrated — per-slice planning detail. Frozen here. |
| `quick/N-SUMMARY.md` | Not migrated — quick-task records. Frozen here. |
| `forensics/report-*.md` | Not migrated — incident reports from 2026-05-23. Frozen here. |

## What was dropped on the floor

These files were copied during the migration but **deleted before this archive was finalized** because they had no historical value:

- `CODEBASE.md` — auto-generated file listing, stale on capture
- `KNOWLEDGE.md` — empty table stubs
- `CONTEXT.md` — GSD's auto-detected "Primary: java" stub
- `PREFERENCES.md`, `STATE.md`, `last-snapshot.md`, `repo-meta.json` — GSD framework config and runtime state
- Everything under `runtime/`, `audit/`, `journal/`, `activity/`, `worktrees/`, `graphs/`
- `gsd.db*` (SQLite), `metrics.json`, `event-log.jsonl`, `notifications.jsonl`, `doctor-history.jsonl`, `state-manifest.json`
- All `*-VERIFY.json` and `*-PRE-EXEC-VERIFY.json` task verification files

## Reading order if you came here looking for "why did we do X"

1. **`DECISIONS.md`** — the most likely answer is in one of the 36 rows.
2. **`milestones/M00X/M00X-CONTEXT.md`** — milestone-scoped scope and reasoning.
3. **`milestones/M00X/slices/SNN/SNN-SUMMARY.md`** — per-slice outcomes and any deviations noted at execution time.
4. **`milestones/M001/M001-LEARNINGS.md`** — the M001 retrospective (most has been promoted but the full version is here for completeness).
