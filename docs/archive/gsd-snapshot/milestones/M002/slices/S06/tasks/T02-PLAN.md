---
estimated_steps: 6
estimated_files: 2
skills_used:
  - svelte-code-writer
---

# T02: Remove unused layerchart dependency and verify Fraunces font rendering

**Slice:** S06 — Deferred Fixes & Backend Tests
**Milestone:** M002

## Description

Layerchart has zero imports in frontend/src/ — it is dead weight that creates a false Svelte 5 compatibility concern (R012). Fraunces font (R013) is configured in layout.css @theme inline block as --font-heading and used in 5 UI components via the font-heading Tailwind utility. It needs visual verification and a fix if not rendering.

## Steps

1. Remove `layerchart` from `frontend/package.json` dependencies.
2. Run `bun install` to update bun.lock.
3. Run `cd frontend && npx vite build` to confirm build still passes without layerchart.
4. Verify Fraunces font rendering: start the dev server, navigate to a page with card-title or dialog-title elements, and confirm the heading text uses Fraunces Variable (check computed font-family in browser devtools or take a screenshot). The CSS config looks correct (--font-heading defined in @theme inline, font-heading class used in components) so this is likely a verification-only outcome.
5. If Fraunces is NOT rendering: diagnose whether Tailwind v4 @theme inline is generating the font-heading utility correctly. Possible fix: ensure the --font-heading variable name follows Tailwind v4 conventions for font family utilities.
6. Run `cd frontend && npx vite build` final check.

## Must-Haves

- [ ] layerchart removed from frontend/package.json
- [ ] bun.lock updated (no layerchart references)
- [ ] Fraunces font confirmed rendering on heading elements (or fixed)
- [ ] vite build passes clean

## Verification

- `cd frontend && npx vite build`
- `grep -qv layerchart frontend/package.json`

## Inputs

- `frontend/package.json`
- `frontend/src/routes/layout.css`
- `frontend/src/lib/components/ui/card/card-title.svelte`

## Expected Output

- `frontend/package.json`
- `frontend/bun.lock`
