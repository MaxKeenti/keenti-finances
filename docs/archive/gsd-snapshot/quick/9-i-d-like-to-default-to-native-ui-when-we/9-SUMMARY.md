# Quick Task: I'd like to default to native ui when we're on mobile, like date pickers, selects and such

**Date:** 2026-05-15
**Branch:** gsd/quick/9-i-d-like-to-default-to-native-ui-when-we

## What Changed
- Created `NativeSelect` wrapper component: detects touch-primary devices via `window.matchMedia('(hover: none) and (pointer: coarse)')` and renders a native `<select>` on mobile or the custom bits-ui Select (trigger + popover content) on desktop.
- Created `NativeDatePicker` wrapper component: renders `<input type="date">` on mobile or the existing Popover + Calendar picker on desktop. Handles the hidden input (needed for form submission on desktop) internally.
- Mobile detection is reactive — updates if the pointer type changes (e.g. keyboard/mouse attached to a tablet).
- Replaced all Select and date picker usages across five pages: `transactions`, `debts`, `debts/[id]`, `subscriptions`, `categories`. Removed now-unused imports (`parseDate`, `Calendar`, `Popover`, `Select`, `CalendarIcon`) from each page.

## Files Modified
- `frontend/src/lib/components/native-select/native-select.svelte` (new)
- `frontend/src/lib/components/native-select/index.ts` (new)
- `frontend/src/lib/components/native-date-picker/native-date-picker.svelte` (new)
- `frontend/src/lib/components/native-date-picker/index.ts` (new)
- `frontend/src/routes/transactions/+page.svelte`
- `frontend/src/routes/debts/+page.svelte`
- `frontend/src/routes/debts/[id]/+page.svelte`
- `frontend/src/routes/subscriptions/+page.svelte`
- `frontend/src/routes/categories/+page.svelte`

## Verification
- `tsc --noEmit`: no new errors in routes or new components (pre-existing node_modules errors unchanged)
- `vite build`: completed successfully with no new warnings
