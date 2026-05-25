---
estimated_steps: 1
estimated_files: 1
skills_used: []
---

# T02: Write Railway deployment checklist with env var manifest

Create DEPLOY-M002.md covering frontend env vars, backend env vars, WorkOS dashboard config, Flyway migrations, post-deploy verification, and rollback notes.

## Inputs

- `frontend/src/lib/server/workos.ts`
- `frontend/src/hooks.server.ts`
- `backend/src/main/resources/application.properties`

## Expected Output

- `DEPLOY-M002.md with 6 sections and ORIGIN warning`

## Verification

test -f DEPLOY-M002.md
