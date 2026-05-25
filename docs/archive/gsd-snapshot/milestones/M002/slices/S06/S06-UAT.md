# S06: Deferred Fixes & Backend Tests — UAT

**Milestone:** M002
**Written:** 2026-05-18T00:07:11.803Z

# S06 UAT: Deferred Fixes & Backend Tests

## 1. Backend Integration Tests Pass

```bash
cd backend && ./mvnw test
```
Expected: `Tests run: 12, Failures: 0, Errors: 0, Skipped: 0` — BUILD SUCCESS

## 2. layerchart Removed

```bash
grep layerchart frontend/package.json
```
Expected: no output (exit 1 — not found)

```bash
grep -c layerchart frontend/bun.lock
```
Expected: `0`

## 3. Frontend Build Clean

```bash
cd frontend && npx vite build
```
Expected: exit 0, `✔ done` with no errors

## 4. Fraunces Font in Built CSS

After the build above:
```bash
grep -o 'font-heading[^;]*' frontend/.svelte-kit/output/client/_app/immutable/assets/*.css
```
Expected: `.font-heading{font-family:Fraunces Variable,serif}` (or similar match)

