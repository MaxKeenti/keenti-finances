---
estimated_steps: 12
estimated_files: 1
skills_used: []
---

# T03: Add theme detection script to app.html for S02 readiness

Why: S02 depends on the .dark class toggle point on <html>. The inline script in app.html must run before first paint to prevent flash. Adding it in S01 establishes the contract S02 consumes without adding visual changes yet.

Do:
1. Add inline script to `frontend/src/app.html` inside <head> before %sveltekit.head%:
   ```html
   <script>
     if (window.matchMedia('(prefers-color-scheme: dark)').matches) {
       document.documentElement.classList.add('dark');
     }
   </script>
   ```
2. This enables the existing .dark {} CSS block in layout.css to activate based on system preference.

Done when: app.html contains the inline prefers-color-scheme script setting .dark class on html element.

## Inputs

- `frontend/src/app.html`

## Expected Output

- `frontend/src/app.html`

## Verification

grep -q 'prefers-color-scheme' frontend/src/app.html
