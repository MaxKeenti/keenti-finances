---
id: T03
parent: S04
milestone: M002
key_files:
  - frontend/src/routes/subscriptions/+page.svelte
  - frontend/src/routes/debts/+page.svelte
key_decisions:
  - Used CSS stretched-link pattern (absolute inset-0 <a> + relative z-[1] on button container) instead of JS goto() — no script import needed, degrades gracefully, works with SvelteKit's built-in link handling
  - Removed title-only <a> wrappers since the stretched link already covers the full card surface
  - Kept the explicit View/Payments buttons as secondary affordances (visible CTA) even though the whole card is now tappable
duration: 
verification_result: passed
completed_at: 2026-05-17T17:28:23.971Z
blocker_discovered: false
---

# T03: Made subscription and debt cards fully tappable via CSS stretched-link pattern; action buttons remain independently clickable via z-index layering

**Made subscription and debt cards fully tappable via CSS stretched-link pattern; action buttons remain independently clickable via z-index layering**

## What Happened

Both subscriptions and debts already had partial links (title-only `<a>` tags). Replaced them with the CSS stretched-link pattern: each `Card.Root` gets `relative`, and an absolutely-positioned `<a href="…" class="absolute inset-0 …">` is inserted as the first child, covering the entire card surface. The action buttons container gets `relative z-[1]`, which places buttons in a new stacking context above the transparent stretched link — clicks on buttons go to the button handlers, clicks anywhere else on the card trigger navigation. Removed the now-redundant title `<a>` wrappers. The existing `grid gap-4 sm:grid-cols-2 xl:grid-cols-3` grid on both pages already collapses to single column at ≤640px.

## Verification

Ran `cd frontend && npx vite build` from the main project directory. Build exits 0 with "✔ done".

## Verification Evidence

| # | Command | Exit Code | Verdict | Duration |
|---|---------|-----------|---------|----------|
| 1 | `cd /Users/moonstone/Source/Personal/keenti-finances/frontend && npx vite build` | 0 | pass | 35000ms |

## Deviations

none

## Known Issues

none

## Files Created/Modified

- `frontend/src/routes/subscriptions/+page.svelte`
- `frontend/src/routes/debts/+page.svelte`
