# D1 — Balance presentation and tracking source

Status: accepted as the implementation starting point following the product owner’s merge of PR #36 and request to continue on 14 September 2026. Accounting formulas remain unchanged.

## Decision

Reuse `GET /api/accounts/status` rather than add a backend contract. `AccountTrackingStatusResponse` already supplies `active`, `setupRequired`, `activatedAt`, `transactionNetBalance` and `accountNetBalance`. The layout currently reads this response to redirect setup but does not expose a validated tracking-mode section. Expose its authoritative boolean through a validated available/unavailable section for downstream presentation. Never infer mode from whether totals are equal, nonzero, or whether an account list is empty.

| Authoritative mode | Net Balance explanation |
|---|---|
| active = false | Recorded all-time income minus expenses |
| active = true | Sum of signed Financial Account balances |
| Failed/malformed response or missing active boolean | Tracking information unavailable; Retry. Show a successfully loaded total without guessing its source formula. |

If tracking mode is known but the balance read fails, retain the mode explanation and show the total as unavailable with Retry. If both reads fail, both sections are unavailable. Neither failure may produce a zero balance.

Account tracking activation and the stored ledger remain unchanged. Available to Spend remains Net Balance minus In Boxes in both modes. Describe it as money unallocated to Boxes, with an explicit note that unrecorded future essentials, subscriptions and expected receipts are not a cash forecast. An existing recorded card purchase is already reflected; an unpaid statement must not subtract it again.

## Labels

| Meaning | English | Spanish |
|---|---|---|
| Negative signed Credit Financial Account balance, displayed as magnitude | Credit debt | Deuda de crédito |
| Positive signed Credit Financial Account balance | Credit in your favor | Crédito a favor |
| Zero signed Credit Financial Account balance | No credit debt | Sin deuda de crédito |
| Limit-derived capacity, only when a limit exists | Available credit | Crédito disponible |
| Confirmed statement amount still unpaid | Outstanding statement payment | Pago pendiente del estado de cuenta |

Available credit stays outside Net Balance, In Boxes, Available to Spend and money held. Statement outstanding is distinct from current credit balance; due-date presentation belongs to D2. Positive credit balance is not promised to be spendable cash in another account.

## Negative-state examples

- Net 1,000, In Boxes 1,300, Available −300: reservations exceed Net Balance by 300. Show the breakdown; offer reviewing reservations and only offer withdrawal from a Box with money.
- Net −200, In Boxes 0: the signed recorded position is negative. Withdrawing from an empty Box cannot resolve it.
- Confirmed statement `reconciliationMismatch=true`: separately offer reviewing recorded activity versus the confirmed snapshot. A negative Available total alone does not prove an incorrect record.
- Net 0, In Boxes 0 with successful reads: legitimate zero. A failed read must never become these figures.

Implementation verification uses the existing 0A fixtures and the synthetic `FX-DASH-NEG-01` contract, not live records. Keep failed balance and failed tracking metadata independent. No database migration or new balance formula is proposed.

## Source anchors

- `CONTEXT.md`: Net Balance, Available to Spend, Financial Account.
- ADR-0022: signed ledger, opt-in tracking, neutral Transfers and statement snapshots.
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/AccountTrackingStatusResponse.java`.
- `frontend/src/routes/+layout.server.ts`: existing account-status read and setup redirect.

Implementation follows these labels, source reuse and unknown-mode behavior.
