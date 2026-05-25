---
estimated_steps: 19
estimated_files: 5
skills_used: []
---

# T02: Configure SvelteKit frontend for production deployment

Switch from adapter-auto to adapter-node for Railway Node.js deployment, make the backend proxy URL configurable via BACKEND_URL env var, enforce SESSION_SECRET in production, and create a Dockerfile.

## Steps

1. Install `@sveltejs/adapter-node` (bun add -D @sveltejs/adapter-node). Remove `@sveltejs/adapter-auto` if present.
2. Update `frontend/svelte.config.js`: import adapter from `@sveltejs/adapter-node` instead of `adapter-auto`.
3. Update `frontend/src/routes/api/[...path]/+server.ts`: replace hardcoded `const BACKEND = 'http://localhost:8080'` with `const BACKEND = process.env.BACKEND_URL ?? 'http://localhost:8080'` so Railway can point it to the internal Quarkus service URL.
4. Update `frontend/src/lib/server/session.ts`: in production (`process.env.NODE_ENV === 'production'`), throw an error if `SESSION_SECRET` env var is not set instead of falling back to the dev secret.
5. Create `frontend/Dockerfile`: multi-stage build — stage 1 installs deps with `bun install`, builds with `bun run build`; stage 2 uses a slim Node.js 22 image, copies `build/` directory and `package.json`, runs `node build/index.js`. Expose port 3000.
6. Verify: `bun run build` succeeds, adapter-node in svelte.config.js, BACKEND_URL in proxy file, Dockerfile exists.

## Must-Haves

- [ ] adapter-node installed and configured in svelte.config.js
- [ ] BACKEND_URL env var used in proxy +server.ts (with localhost fallback for dev)
- [ ] SESSION_SECRET required in production (throws if unset)
- [ ] Dockerfile that builds and runs the SvelteKit app

## Verification

- `cd frontend && bun run build` exits 0
- `grep -q 'adapter-node' frontend/svelte.config.js`
- `grep -q 'BACKEND_URL' frontend/src/routes/api/\[...path\]/+server.ts`
- `grep -q 'SESSION_SECRET' frontend/src/lib/server/session.ts`
- `test -f frontend/Dockerfile`

## Inputs

- `frontend/package.json`
- `frontend/svelte.config.js`
- `frontend/src/routes/api/[...path]/+server.ts`
- `frontend/src/lib/server/session.ts`

## Expected Output

- `frontend/package.json`
- `frontend/svelte.config.js`
- `frontend/src/routes/api/[...path]/+server.ts`
- `frontend/src/lib/server/session.ts`
- `frontend/Dockerfile`

## Verification

cd frontend && bun run build && grep -q 'adapter-node' svelte.config.js && grep -q 'BACKEND_URL' src/routes/api/\[...path\]/+server.ts && test -f Dockerfile
