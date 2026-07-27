# Railway service watch-path evidence — 2026-07-26

## Scope

- Project: `keenti-finances` (`4ed3c804-4f91-4b43-a912-1ba7de9accc0`)
- Environment: `production` (`a08caa22-994e-4dbd-92c2-7cf5eaa698e9`)
- Backend: `backend` (`8949110a-ed94-4976-9ec5-cfc229ff889c`)
- Frontend: `frontend` (`55a08c17-2836-4615-a3e7-ab492c85e450`)

No variables or secret values were read or recorded.

## Reason

Both application services previously had empty watch-pattern arrays. A
documentation-only commit therefore rebuilt both images and woke both services.
That behavior consumed build time and idle-sensitive RAM without changing either
application.

## Configuration

The live production settings now use isolated repository paths:

| Service | Before | After |
| --- | --- | --- |
| Backend | `[]` | `["/backend/**"]` |
| Frontend | `[]` | `["/frontend/**"]` |

Railway Serverless remains enabled for frontend, backend, and PostgreSQL.
Applying the watch paths did not create a deployment or wake either application
service.

## Docs-only acceptance

Baseline immediately after the configuration read-back:

| Service | Deployment ID | State |
| --- | --- | --- |
| Backend | `e9ea0f97-06a0-4e7d-bd74-20b40a3250f3` | `SLEEPING` |
| Frontend | `f78ce332-4693-479c-993a-412581aed44c` | `SLEEPING` |

Commit `a1554deaa229030f3bae0b72ce5191bc7909690c` changed only this evidence
file and the HTML improvement plan. Railway processed the repository update and
created a terminal `SKIPPED` record for each service. It did not build an image,
create a new active deployment, or wake either service.

| Observation | Backend | Frontend |
| --- | --- | --- |
| Skipped record ID | `30b6faca-b76d-4899-b1bb-4ecee22dd882` | `28909b3d-3750-4d19-bb5c-4640ec8eb1f0` |
| Active deployment ID | `e9ea0f97-06a0-4e7d-bd74-20b40a3250f3` | `f78ce332-4693-479c-993a-412581aed44c` |
| Active service state | `SLEEPING` | `SLEEPING` |

Acceptance passed: both active deployment IDs match the baseline, and both
services remained sleeping.

## Decision

Keep isolated watch paths and Railway Serverless enabled. Backend changes should
deploy only the backend, frontend changes should deploy only the frontend, and
repository-root documentation changes should deploy neither service.
