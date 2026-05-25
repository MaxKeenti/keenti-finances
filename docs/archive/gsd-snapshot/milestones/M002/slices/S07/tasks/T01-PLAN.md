---
estimated_steps: 1
estimated_files: 5
skills_used: []
---

# T01: Pre-deploy build and test verification

Run backend Maven build, 12 integration tests, frontend Vite build, svelte-check, and verify Flyway V7-V9 migrations exist and are valid SQL.

## Inputs

- `backend/src/test/java/`
- `frontend/src/`

## Expected Output

- `All commands exit 0`
- `12/12 backend tests pass`
- `Vite build produces output`
- `svelte-check 0 app errors`

## Verification

./mvnw test -f backend/pom.xml && cd frontend && npx vite build && npx svelte-check --threshold error
