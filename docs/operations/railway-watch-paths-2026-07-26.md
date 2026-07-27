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

This evidence file and the HTML improvement-plan update form a docs-only commit.
After that commit is pushed, acceptance requires both latest deployment IDs to
remain unchanged and both services to remain sleeping. The final observation is
recorded below after Railway and GitHub have processed the push.

| Observation | Backend | Frontend |
| --- | --- | --- |
| Latest deployment ID | Pending | Pending |
| Service state | Pending | Pending |

## Decision

Keep isolated watch paths and Railway Serverless enabled. Backend changes should
deploy only the backend, frontend changes should deploy only the frontend, and
repository-root documentation changes should deploy neither service.
