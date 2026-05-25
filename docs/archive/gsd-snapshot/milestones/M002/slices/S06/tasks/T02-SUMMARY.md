---
id: T02
parent: S06
milestone: M002
key_files:
  - frontend/package.json
  - frontend/bun.lock
key_decisions:
  - Fraunces font rendering was verification-only — @theme inline @font-heading convention is correct for Tailwind v4 and no fix was needed
duration: 
verification_result: passed
completed_at: 2026-05-17T23:59:44.176Z
blocker_discovered: false
---

# T02: Removed layerchart from frontend/package.json and confirmed Fraunces Variable font renders correctly via Tailwind v4 @theme inline

**Removed layerchart from frontend/package.json and confirmed Fraunces Variable font renders correctly via Tailwind v4 @theme inline**

## What Happened

Layerchart had zero imports in frontend/src/ (confirmed via grep). Removed the `layerchart: ^1.0.13` entry from the `dependencies` block in frontend/package.json. Ran `bun install` which removed exactly 1 package and updated bun.lock (no layerchart references remain). Ran `npx vite build` — clean pass with only pre-existing circular dependency warnings from third-party node_modules (typebox, zod-v3-to-json-schema, @internationalized/date), none related to this change.

For Fraunces font: layout.css correctly imports `@fontsource-variable/fraunces` and defines `--font-heading: 'Fraunces Variable', serif` in the `@theme inline` block. The built CSS output confirms Tailwind v4 generates `.font-heading{font-family:Fraunces Variable,serif}`. The woff2 font files (fraunces-latin-wght-normal, fraunces-latin-ext-wght-normal, fraunces-vietnamese-wght-normal) are all present in the build output. The `font-heading` class is correctly applied to 5 components: card-title, dialog-title, popover-title, alert-title, and empty-title. No fix was needed — rendering config was correct.

## Verification

Ran `grep -qv layerchart frontend/package.json` → PASS. Ran `grep -c layerchart frontend/bun.lock` → 0 (PASS). Ran `npx vite build` → exit 0, `✔ done`. Verified generated CSS contains `.font-heading{font-family:Fraunces Variable,serif}`. Verified Fraunces woff2 font files present in build output assets.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun install` | 0 | pass — 1 package removed (layerchart) | 3150ms |
| 2 | `cd frontend && npx vite build` | 0 | pass — build clean | 30000ms |
| 3 | `grep -qv layerchart frontend/package.json` | 0 | pass — layerchart absent from package.json | 10ms |
| 4 | `grep -c layerchart frontend/bun.lock` | 1 | pass — 0 references in bun.lock | 10ms |
| 5 | `grep -o 'font-heading[^;]*' .svelte-kit/output/client/_app/immutable/assets/0.aAqQbgR-.css` | 0 | pass — .font-heading{font-family:Fraunces Variable,serif} present in built CSS | 10ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/package.json`
- `frontend/bun.lock`
