# Quick Task: Our selects are still not showing the text the ID corresponds to, just the plain ID

**Date:** 2026-05-15
**Branch:** gsd/quick/8-our-selects-are-still-not-showing-the-te

## Root Cause

bits-ui's `Select.Value` resolves the displayed label by looking at mounted `Select.Item` nodes. Items only mount when the dropdown is open (they're in a portal/content). In edit mode, the form value is set programmatically before the dropdown is ever opened, so no items are mounted yet and `Select.Value` renders nothing (just the raw ID or blank).

bits-ui v2 exposes an `items` prop on `Select.Root` (`{ value: string, label: string }[]`) that pre-loads a label lookup table, allowing `getLabelForValue()` to resolve the label without the content being rendered.

## What Changed

- Added `items={...map(c => ({ value: String(c.id), label: c.name }))}` to every `Select.Root` that uses numeric entity IDs as values (contacts, categories)
- Also added missing `label` prop to a `Select.Item` in subscriptions that had the child text but no explicit label attribute

## Files Modified

- `frontend/src/routes/debts/+page.svelte` — contact select (create/edit), contact select (bulk payment), category select (bulk payment)
- `frontend/src/routes/debts/[id]/+page.svelte` — ingress category select (add payment form)
- `frontend/src/routes/transactions/+page.svelte` — category select, contact select
- `frontend/src/routes/subscriptions/+page.svelte` — category select

## Verification

- `bun run check` passes with same pre-existing errors (node_modules/effect, Calendar type issues) — zero new errors introduced
- Selects using string enum values (`bind:value` with "INGRESS"/"EGRESS", "MONTHLY"/"YEARLY") were already working and left unchanged
