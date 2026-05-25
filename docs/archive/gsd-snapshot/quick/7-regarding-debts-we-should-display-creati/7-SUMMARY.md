# Quick Task: Regarding debts, we should display creation date as a date picker so we can easily select if a debt was made in the past and now we're catching up, also, add the ability to edit said date through the edit dialog

**Date:** 2026-05-15
**Branch:** gsd/quick/7-regarding-debts-we-should-display-creati

## What Changed
- Added `createdAt` (date picker) field to the New Debt and Edit Debt dialogs — defaults to today, fully editable for backdating past debts
- `DebtRequest.createdAt` (already existed as `LocalDate`) is now wired through: `DebtResource.create()` converts it to `LocalDateTime` and passes it to the domain; `DebtResource.update()` uses the provided date or falls back to the existing one
- `PanacheDebtRepository.toEntity()` now sets `createdAt` on the entity (previously ignored it, leaving the DB default always); `update()` also persists `createdAt` changes
- Debt cards now display the creation date when present
- `debtSchema` in both `+page.server.ts` and `+page.svelte` includes `createdAt: z.string().min(1, 'Date is required')`

## Files Modified
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/DebtResource.java`
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/out/persistence/PanacheDebtRepository.java`
- `frontend/src/routes/debts/+page.server.ts`
- `frontend/src/routes/debts/+page.svelte`

## Verification
- `bun run check` — 9 errors (down from 10), all remaining are pre-existing; no new type errors introduced
- New Calendar uses `type="single"` to avoid the multi-value type mismatch that affects other Calendars in the codebase
