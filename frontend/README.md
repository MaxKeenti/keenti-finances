# Keenti Finances — Frontend

SvelteKit (Svelte 5) app for Keenti Finances, served by `adapter-node`. It owns authentication (WorkOS passkeys, ADR-0002 / ADR-0004) and is the only public entry point. Server code talks to the backend directly; the browser only calls `/api/*`, which `src/routes/api/[...path]/+server.ts` proxies to the backend with the User's `X-WorkOS-User-Id` (ADR-0003).

Standards for working in this code live in [`AI_RULES.md`](../AI_RULES.md); domain language in [`CONTEXT.md`](../CONTEXT.md).

Use **bun** for everything — not npm.

## Layout

| Path | Holds |
|---|---|
| `src/routes/<feature>/` | Pages: `+page.server.ts` (load + form actions) and `+page.svelte` |
| `src/routes/layout.css` | Theme: semantic colour tokens, per-User hue and fonts |
| `src/lib/components/ui/` | shadcn-svelte generated primitives only |
| `src/lib/components/<feature>/` | Hand-written components, exported from each folder's `index.ts` |
| `src/lib/server/` | Server-only code: backend fetch, payload parsing, section loading, WorkOS session |
| `src/lib/*.ts` | Pure shared logic: formatting, labels, obligation status, planning |
| `messages/en.json`, `messages/es.json` | Paraglide messages — every user-facing string |
| `tests/` | `bun test` suites and synthetic fixtures (`tests/fixtures/`) |

## Running locally

```shell
bun install
bun run dev
```

Environment variables (see [`DEPLOY.md`](../DEPLOY.md) for production values):

| Variable | Local use |
|---|---|
| `BACKEND_URL` | Defaults to `http://localhost:8080` |
| `WORKOS_API_KEY`, `WORKOS_CLIENT_ID`, `WORKOS_COOKIE_PASSWORD` | Needed for real passkey login |
| `TEST_AUTH_BYPASS=true` | Skips WorkOS and signs in as a demo User (`TEST_WORKOS_USER_ID`, default `development-demo`). Refused when `NODE_ENV=production` |

### Against fixtures instead of a backend

`tests/fixtures/server.ts` serves one synthetic scenario over HTTP, read-only and on loopback only:

```shell
bun run tests/fixtures/server.ts FX-BAL-ZERO-01      # listens on :8099 (FIXTURE_PORT)
BACKEND_URL=http://localhost:8099 TEST_AUTH_BYPASS=true bun run dev
```

See [`tests/fixtures/README.md`](tests/fixtures/README.md) for scenarios and failure injection.

## Checks

```shell
bun run check        # Paraglide compile + svelte-check
bun run build        # runs lint:colors first (prebuild), then vite build
bun run test         # bun test
bun run lint:colors  # raw palette / arbitrary colour gate on its own
```

CI runs `check`, `build`, and `test`, then builds the production Docker image.

## Deployment

Built from `Dockerfile` (bun build, Node runtime) on Railway. `ORIGIN` must be set in production or the WorkOS callback fails. See [`DEPLOY.md`](../DEPLOY.md).
