# Quick Task: Could we add a railway toml for easy deployment?

**Date:** 2026-05-14
**Branch:** gsd/quick/1-could-we-add-a-railway-toml-for-easy-dep

## What Changed
- Added `railway.toml` to `backend/` — specifies Dockerfile builder, `/q/health/live` health check (300s timeout), and ON_FAILURE restart policy
- Added `railway.toml` to `frontend/` — specifies Dockerfile builder, `/` health check (120s timeout), and ON_FAILURE restart policy

## Files Modified
- `backend/railway.toml` (new)
- `frontend/railway.toml` (new)

## Verification
- Both files are valid TOML with correct Railway v2 schema fields (`[build]`, `[deploy]`, `builder`, `dockerfilePath`, `healthcheckPath`, `healthcheckTimeout`, `restartPolicyType`, `restartPolicyMaxRetries`)
- Health check paths match existing Quarkus SmallRye Health endpoint (`/q/health/live`) and SvelteKit root route
- Committed as `364d3bc`
