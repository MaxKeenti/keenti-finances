# Quick Task: Let's add a bulk payment feature for debts: Whenever a contact transfers a sum of money that covers many debts in a single payment, then automatically splits debts and pays the oldest debt until this bulk payment has been used, even making partial payments to newer debts, for this, we need to record when the debt was placed so maybe a V7 flyway migration schema is necessary

**Date:** 2026-05-15
**Branch:** gsd/quick/6-let-s-add-a-bulk-payment-feature-for-deb

## What Changed
- No migration needed — `created_at` already existed on the `debt` table (V5) and was already mapped in the domain model
- Added `findActiveByContactIdOrderByCreatedAt(Long contactId)` to `DebtRepository` interface and `PanacheDebtRepository`
- Added `BulkPaymentResult` / `BulkPaymentItem` inner records to `DebtUseCase` interface + `bulkPayment(...)` method
- Implemented `DebtService.bulkPayment`: iterates active debts for contact ordered by `createdAt ASC`, applies lump sum greedily, creates individual `DebtPayment` + `Transaction` records per debt, marks debts `PAID` when balance zeroed, returns breakdown of applied/remaining/unused
- Added `BulkPaymentRequest`, `BulkPaymentItemResponse`, `BulkPaymentResponse` records (REST layer)
- Added `POST /api/debts/bulk-payment` endpoint in `DebtResource`
- Frontend debts list: added `bulkPayment` action in `+page.server.ts`, fetches categories on load
- Frontend: added "Bulk Payment" button in page header; dialog with contact/amount/date/category form; on success shows a breakdown table (applied, remaining, status per debt) and lets user apply another

## Files Modified
- `backend/.../domain/port/in/DebtUseCase.java`
- `backend/.../domain/port/out/DebtRepository.java`
- `backend/.../application/service/DebtService.java`
- `backend/.../infrastructure/adapter/in/rest/DebtResource.java`
- `backend/.../infrastructure/adapter/in/rest/BulkPaymentRequest.java` (new)
- `backend/.../infrastructure/adapter/in/rest/BulkPaymentItemResponse.java` (new)
- `backend/.../infrastructure/adapter/in/rest/BulkPaymentResponse.java` (new)
- `backend/.../infrastructure/adapter/out/persistence/PanacheDebtRepository.java`
- `frontend/src/routes/debts/+page.server.ts`
- `frontend/src/routes/debts/+page.svelte`

## Verification
- Backend compiled clean (`mvnw compile` — no errors)
- No TypeScript errors in debts routes (`tsc --noEmit` — zero errors in `src/routes/debts/`)
- Pre-existing errors in `node_modules/effect` and shadcn re-exports are unrelated to this task
