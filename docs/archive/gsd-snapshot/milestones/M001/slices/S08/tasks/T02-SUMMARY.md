---
id: T02
parent: S08
milestone: M001
key_files:
  - frontend/svelte.config.js
  - frontend/src/routes/api/[...path]/+server.ts
  - frontend/src/lib/server/session.ts
  - frontend/Dockerfile
  - frontend/package.json
key_decisions:
  - SESSION_SECRET check made lazy (inside getSessionSecret() called at request time) rather than at module load time — SvelteKit post-build analysis imports server modules with NODE_ENV=production, so an eager throw breaks the build
  - build script simplified from 'vite build && npm run prepack' to 'vite build' — prepack ran svelte-package/publint (library tooling) which is incompatible with app deployment
duration: 
verification_result: passed
completed_at: 2026-05-14T20:45:32.044Z
blocker_discovered: false
---

# T02: Switched SvelteKit to adapter-node, wired BACKEND_URL proxy env var, enforced SESSION_SECRET at request time in production, and created multi-stage Dockerfile for Railway deployment

**Switched SvelteKit to adapter-node, wired BACKEND_URL proxy env var, enforced SESSION_SECRET at request time in production, and created multi-stage Dockerfile for Railway deployment**

## What Happened

Implemented all production deployment configuration for the SvelteKit frontend:

1. **adapter-node install**: Ran `bun add -D @sveltejs/adapter-node` (v5.5.4 installed). Also discovered and installed missing `@fontsource-variable/fraunces` which was imported in `layout.css` but absent from package.json, causing the build to fail.

2. **svelte.config.js**: Replaced `@sveltejs/adapter-auto` import with `@sveltejs/adapter-node` and removed the now-stale adapter-auto comment.

3. **package.json build script**: The original script was `vite build && npm run prepack` where `prepack` ran `svelte-package && publint` — library-publishing tooling incompatible with app deployment. Updated to just `vite build`.

4. **Proxy +server.ts**: Replaced hardcoded `'http://localhost:8080'` with `process.env.BACKEND_URL ?? 'http://localhost:8080'` so Railway can point the proxy to the internal Quarkus service.

5. **session.ts**: Added production guard for SESSION_SECRET. The check was initially placed at module load time, which caused a build failure because SvelteKit's post-build analysis imports server modules with NODE_ENV=production. Moved the check inside a `getSessionSecret()` helper called at request time, making it lazy — safe for build analysis but still enforced on every real request.

6. **Dockerfile**: Multi-stage build — stage 1 uses `oven/bun:1` to install deps and run `bun run build`, stage 2 uses `node:22-slim` to copy only the `build/` output and `package.json`, exposes port 3000, sets NODE_ENV=production, runs `node build/index.js`.

## Verification

Ran `cd frontend && bun run build` — exit 0. Confirmed all grep checks pass: adapter-node in svelte.config.js, BACKEND_URL in proxy server.ts, SESSION_SECRET in session.ts, Dockerfile present.

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd frontend && bun run build` | 0 | pass | 18000ms |
| 2 | `grep -q 'adapter-node' frontend/svelte.config.js` | 0 | pass | 10ms |
| 3 | `grep -q 'BACKEND_URL' "frontend/src/routes/api/[...path]/+server.ts"` | 0 | pass | 10ms |
| 4 | `grep -q 'SESSION_SECRET' frontend/src/lib/server/session.ts` | 0 | pass | 10ms |
| 5 | `test -f frontend/Dockerfile` | 0 | pass | 10ms |

## Deviations

Installed @fontsource-variable/fraunces (missing dep referenced in layout.css but absent from package.json, which caused build failure). Fixed library-template build script — original script chained npm run prepack which ran svelte-package and publint, incompatible with SvelteKit app deployment.

## Known Issues

None.

## Files Created/Modified

- `frontend/svelte.config.js`
- `frontend/src/routes/api/[...path]/+server.ts`
- `frontend/src/lib/server/session.ts`
- `frontend/Dockerfile`
- `frontend/package.json`
