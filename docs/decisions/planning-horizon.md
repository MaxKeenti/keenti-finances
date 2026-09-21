# D5 — 30-day planning horizon: stateless scenario preview

Status: **accepted**, 20 September 2026. The product owner approved A1–A5 and
this contract after merging [PR #41](https://github.com/MaxKeenti/keenti-finances/pull/41)
and explicitly requested implementation. Slice 5A supplies the calculation and
fixtures; 5B (reads/API) and 5C (interface) remain to implement. The feature is not
exposed until all three slices pass. See [implementation status](../features/planning-preview.md).

## Approved choices

| # | Choice | Approved | Real alternative being given up |
|---|---|---|---|
| A1 | First release is a **stateless what-if preview**: costs are typed into the form, nothing is stored | yes | persist Planned Costs from day one — needs the full completion/dedup machinery (see A4) before it is safe |
| A2 | Two independent outputs: a **projected-unallocated** number for *unrecorded* costs, and a **separate timing list** of already-recorded dated obligations | yes | an account-by-account liquidity forecast — requires dated flows and explicit settlement accounts |
| A3 | Expected income is opt-in per receipt, each with a user-chosen scenario date | yes | auto-include every PENDING/INGRESS record — most carry no date |
| A4 | Persisted planned costs, subscription/debt origin links and actual-Transaction completion are **deferred** to a separately approved extension | yes | implement persistence and the complete reconciliation lifecycle in the first release, increasing scope |
| A5 | Horizon = 30 calendar days `[today, today+29]` in the User's IANA zone | yes | the remainder of the current calendar month, which shortens as the month ends; or a user-chosen length, which we can add later without changing the formula |

## Baseline, and what is already in it

Let `N` = Net Balance, `B` = In Boxes, `U` = Available to Spend = `N − B`
(unchanged; this decision adds no term to it).

Two source facts constrain everything below, verified in the repo:

- `TransactionService.normalizeAndValidateFunding` rejects future dates **only** for
  Box funding (`TransactionService.java:288`) and Box distributions (`:390`).
  Ordinary Transactions **may** be future-dated.
- `PanacheFinancialAccountRepository.getTotalBalance` and the per-account
  `balance(...)` query sum **all** non-deleted Transactions with no date filter
  (`:155`, `:226`). A separate `getBalanceAt(accountId, date)` does filter by date
  (`:186`), but the dashboard does not use it.

The preview must use the dashboard's baseline branch: account tracking active uses
`getTotalBalance`; otherwise `PanacheTransactionRepository.getNetBalance` (`:137`)
sums Transactions without a date cutoff. Active tracking excludes archived accounts
and their Transactions. All Transactions represented by that baseline, including
future-dated ones, have already affected `N`/`U`; none may be forecast again.
Archived-account history is likewise not a new unrecorded cost. The preview does
not change the dashboard's inclusion rules or reconstruct historical balances.
`N` is a ledger total, not cash on hand today; never present `U` as today's cash.
An as-of-today model would require separate dated reads and a separate decision.

Subject to those inclusion rules, already represented in `N`/`U`, never forecast again: a recorded card purchase;
the full EGRESS of an MSI purchase (ADR-0022); and credit in favor, which is part of
the credit account's recorded balance. Credit in favor is **not** asset cash and must
be labelled as such, but we cannot claim it is excluded from the baseline. Available
credit *capacity* is in neither `N` nor `U` and never enters any output.

## The formula

Inputs are user-entered, per scenario line item *i*:

- `E_i` — cost amount, > 0, not yet recorded as a Transaction
- `d_i` — date, must fall inside the window
- `F_i` — optional explicit Box funding, `0 ≤ F_i ≤ E_i`, naming one Box

Constraint: for each Box *b*, `Σ{i: box(i)=b} F_i ≤ boxBalance(b)` in the server snapshot.

```
N' = N − Σ E_i + Σ R_j
B' = B − Σ F_i
U' = N' − B' = U − Σ (E_i − F_i) + Σ R_j
```

`R_j` are explicitly selected eligible receipts below; their sum defaults to zero.

That is the whole model. A Box-funded peso reduces `N` and `B` equally and leaves
`U` untouched; the un-funded remainder `E_i − F_i` comes out of unallocated money.
There is no separate "coverage gap" figure and nothing is subtracted twice.

**Box capacity is not hand-waved.** If the requested `F` exceeds a Box's balance
there is no invented funding: the projection is unavailable with the per-Box
shortfall until the User lowers
`F` (the remainder then hits `U'`). We never report a Box shortfall alongside an
unchanged `U'`.

**Confirmed Credit Statements are neutral to `N`, `B` and `U'`.** The purchases
behind them already moved `N`. They appear only in the timing list, as dated account
cash-flow context.

Units: MXN, exact decimal cents summed unrounded, rounded half-up once at output.
Dates compared as calendar dates in the User's zone against one captured instant
(D2). A missing or invalid zone makes the horizon unavailable with Retry — never a
UTC fallback.

## Output 1 — projected unallocated

Returns `U` (baseline), `U'`, `N'`, `B'` and per-Box `B'(b)`, all signed. Named
states, never blended:

- **complete** — every entered item has amount, date and valid funding, *and* the
  User has ticked the essentials-review checklist and confirmed each cost is still
  unrecorded.
- **partial** — a subtotal plus an explicit `missingInputs` list. A partial subtotal
  is never presented as the headline "money left".
- **unavailable** — baseline unreadable, zone invalid, or any business-invalid row
  (including a Box-capacity conflict).

The essentials checklist defaults to **not reviewed**. The preview cannot promise
all costs are known; it says "you have entered N costs; recurring essentials such as
groceries are not included unless you added them."

## Output 2 — timing list (separate, additive to nothing)

An ordered list of confirmed obligations due within the window, plus all overdue
unpaid statements, from already-recorded data, shown beside — not
netted into — `U'`. Today that is confirmed Credit Statements with
`outstandingBalance > 0`.

Rules:

- **No inferred funding account.** A statement does not identify the asset account
  that will fund its settlement. Never infer that account or state "cash left after".
- **Overdue unpaid statements never disappear.** A confirmed statement with
  `dueDate` before today stays visible in a dedicated *Overdue* group.
- **Incompleteness is explicit.** Confirmed statements require a due date; a
  missing one is invalid source data. The dedicated read also evaluates current
  statement estimates per active credit account (`estimateCurrentStatement`) to
  identify unconfirmed periods, including MSI, and compares confirmed statements
  for mismatch. These conditions mark timing `partial` with reasons; estimates
  are context, never additional confirmed obligations or deductions. Zero confirmed
  statements does **not** mean zero obligations; it renders as "none confirmed", not
  as a complete picture.
- **Tracking inactive:** timing is `notApplicable`, because the existing credit
  statement service requires tracking to be active. Do not report a failed read.
- **No truncated totals.** The `attention` read caps at 50 items, so a demand total
  derived from it cannot claim completeness. First release requires a dedicated,
  user-scoped statement read that exhausts
  pagination. Failed or incomplete pages make timing unavailable; the existing capped
  attention endpoint cannot supply this list.
- **Box balances need an authoritative read.** Box balances come from a Box read
  covering Boxes with no plan (`FX-BOX-NOPLAN-01`), not from the active-plans
  section.

## Expected income (opt-in, off by default)

Each included receipt requires a selected existing record and an explicit scenario
date inside the window. Eligible records are ACTIVE INGRESS Debts with positive
remaining amounts and PENDING Subscription Member Payment Records with positive
amounts on non-deleted Subscriptions
(the personal charge with no Subscription Member is excluded). Debt remaining amount is total
less recorded payments; Payment Record amount is its full amount, not a new
partial-payment field.
PAID Payment Records are excluded even without a linked Transaction. The server
reloads ownership, status and amounts; it uses the full remaining Debt amount or
full Payment Record amount as `R_j`, rejecting duplicate `(recordKind, recordId)` selections. No partial receipt
amount or inferred receiving account is modeled in this version.

This explicitly extends D2’s “never increases available money” rule:
recorded Available to Spend and dashboard totals remain unchanged; only this
separately labelled hypothetical result can include selected receipts. Approval of
D5 approves that limited extension, not a change to the baseline accounting rule.

Nothing is auto-imported. Selected receipts increase both `N'` and `U'` by `Σ R_j`,
labelled *if received*. Undated EGRESS Debts are not deducted; show INGRESS and
EGRESS Debts separately without netting (ADR-0023). Their dates remain unknown;
a receipt's scenario date is a user assumption, not a promised payment date.

Saving Goal commitments, arrears and Spending Budget top-up suggestions are
informational only (ADR-0021) — they are internal allocations, not spending. A
`SavingGoalRevision.targetDate` is a real future date but is not an expense;
modelling allocations is out of scope for the first release.

## Preview contract

`POST /api/planning/preview` — read-only. It performs **no financial write**: no
billing generated, no cursor advanced, no Transaction, no Box or account movement.
(ADR-0021 lazy plan evaluation remains the only permitted side effect, as in Phase 4.)

Request: `{ horizonDays: 30, essentialsReviewed: boolean, items: [{ amount,
date, description?, boxId?, boxAmount?, notYetRecordedConfirmed: boolean }],
expectedReceipts: [{ recordId, recordKind: DEBT|PAYMENT_RECORD, date }] }`.

Response: `{ generatedAt, window: {from, to}, baseline: {netBalance, inBoxes,
availableToSpend}, projected: {netBalance, inBoxes, availableToSpend, perBox[]},
status: complete|partial|unavailable, missingInputs[], timing: {dated[], overdue[],
status: complete|partial|unavailable|notApplicable, reasons[]}, undatedDebts[], notes[] }`. Unavailable projection is `null`,
never zero. Timing availability is independent of projection availability.

Well-formed requests return a 200 envelope even when business validation fails:
`status: unavailable`, `projected: null`, and `missingInputs` reason codes with row
or Box identifiers; timing is still evaluated independently. Malformed JSON,
structural/type/size errors and authentication failures use normal 4xx responses
without a preview. Thus independent section results apply to accepted requests,
not malformed requests.

Bounds: max 50 costs and 50 receipts; positive amounts ≤ 9,999,999.99 with at most
two decimals; nonnegative funding ≤ cost; descriptions ≤ 200 characters. Unknown
or other-user Box/receipt IDs use the same not-found reason. Flag duplicate
receipts and ineligible statuses. Dates outside the window return
`DATE_OUT_OF_WINDOW`; overfunding returns `FUNDING_EXCEEDS_COST`; cumulative funding
above a Box balance returns `BOX_CAPACITY_EXCEEDED` with the shortfall. Invalid zone
returns `ZONE_UNAVAILABLE`. Business-invalid rows make the entire projection
unavailable, never silently
disappearing from a subtotal.
Incomplete forms are labeled partial locally; the server validates submitted rows
and returns complete only when every cost is confirmed unrecorded and essentials
are reviewed. Otherwise a valid subtotal is partial with explicit missing inputs.

Any permitted lazy plan evaluation completes before balance reads. Each POST then
reads a fresh, consistent, authoritative server snapshot; the client never
supplies balance totals. `generatedAt` is a timestamp, not a revision or a guarantee
that the data has not changed. Do not invent an existing global revision counter.
Changing inputs clears the result. Returning from recording a Transaction or from a
hidden tab clears it and requires the User to reconfirm that costs are still
unrecorded before recalculation. Refresh likewise resets these confirmations.
There is no automatic matching between manual costs and actual Transactions: this
limitation must be visible, and rows require explicit confirmation. Changes in another
session cannot be detected continuously; show the snapshot time and a refresh action.
Drafts live only in page memory, are lost on navigation/reload, and are never retained
in storage; disclose this before entry.

## Explicitly deferred (not approved here)

Persisted planned costs, `originKind`/`originId` links to Subscriptions or Debts,
and `completedTransactionId` completion. Linking a planned cost to a subscription
expense associates a provider expense and is D3 territory regardless of framing, so
it is excluded. Manually entered costs are descriptive only: never auto-linked,
never imported, and they make no provider status claim.

If persisted completion ships later it must ship atomically: create/edit/delete/
restore/refund validation, dedup against the actual Transaction, and prevention of
double allocation or reuse of the same Transaction. Half of that is worse than none.

Also out of scope and labelled so in the UI: horizons beyond 30 days, and any
forecast generated from Box Plan cadence or future plan periods. Plan data is
consumed read-only — Box ids, current balances, and authoritative period/revision
metadata as context — and no expense is generated from a cadence. `nextBillingDate`
is a Payment Record generation cursor (D2), not a provider charge.

## Worked examples

Each case is independent from the same baseline: `N = 10,000.00`,
`B = 3,000.00` (Box *Rent* 2,500.00, Box *Trips* 500.00), `U = 7,000.00`.
Funding here is hypothetical: it neither reserves money nor creates Box Funding.
Actual Box-funded Transactions can only be recorded on or after their date under
the existing validation rule.

Window 2026-09-20 → 2026-10-19, zone `America/Mexico_City`. Figures are invented.

| # | Case | Result |
|---|---|---|
| 1 | Cash cost `E=1,200.00` on 10-02, `F=0` | `N'=8,800.00`, `B'=3,000.00`, `U'=7,000−1,200=5,800.00` |
| 2 | Rent `E=2,500.00` on 10-01, `F=2,500.00` from *Rent* | `N'=7,500.00`, `B'=500.00`, `U'=7,000−0=7,000.00`; Box *Rent* → 0 |
| 3 | Same rent, but *Rent* holds 2,000.00 and *Trips* 1,000.00 (same total `B`) | `F=2,500` rejected (`BOX_CAPACITY_EXCEEDED`, short 500.00). With `F=2,000`: `N'=7,500.00`, `B'=1,000.00`, `U'=7,000−500=6,500.00` |
| 4 | Card purchase 900.00 recorded 09-18; confirmed statement 3,100.00 due 10-05 | `U'=U=7,000.00` — the 900.00 is already in `N`; the statement is timing only, listed on 10-05 |
| 5 | Future card expense 700.00 on 10-08, not yet recorded, `F=0` | `N'=9,300.00`, `U'=6,300.00` — full amount now, hypothetically |
| 6 | Shared subscription 400.00 on 10-03, entered manually as a cost | `E=400.00` (full manual cost, not net of contributions); `U'=6,600.00`. The two 133.33 Subscription Member contributions are separate opt-in receipts; if eligible, opted in and dated, `N'=9,866.66`, `U'=6,600+266.66=6,866.66` — never netted into `E` and never counted twice |
| 7 | MSI purchase 6,000.00 / 6, recorded 09-10 | `U'=7,000.00` — the full EGRESS is already in `N`; the 1,000.00 installments are never a separate charge against `U` |
| 8 | Irregular income: INGRESS Debt 4,500.00, undated | Off by default, `U'=7,000.00`. Opted in with scenario date 10-10: `N'=14,500.00`, `U'=11,500.00`, labelled *if received* |
| 9 | Groceries never entered | `status: partial`, `missingInputs: ["essentials not reviewed"]`; the 7,000.00 is shown as a subtotal, not as money left |
| 10 | Same Contact: owes User 300.00, User owes them 500.00; plus a card with 55.50 credit in favor | Two Debt rows, no 200.00 net, no deduction (EGRESS undated). The 55.50 is already inside `N`, shown as *not cash* |
| 11 | Future-dated EGRESS Transaction 1,100.00 already recorded for 10-12 | `U'=7,000.00` — already in `N` per `getTotalBalance`. It appears as a note that `U` is a ledger total, not today's cash |

Checks: ex.1 `10,000−1,200=8,800`, `8,800−3,000=5,800`. ex.2 `7,500−500=7,000`.
ex.3 `7,500−1,000=6,500`. ex.6 `9,600−3,000=6,600`.

## Fixtures

Reused, verified present in `frontend/tests/fixtures/scenarios.ts`:
`FX-DASH-NEG-01` (negative Available to Spend), `FX-DATE-BOUNDARY-01` (zone
boundary), `FX-PLAN-BUDGET-UNDER-01` (no double subtraction), `FX-BOX-NOPLAN-01`
(Box with no plan), `FX-DEBT-BIDIRECTIONAL-01` (no netting),
`FX-CREDIT-INFAVOR-STMT-01` (credit in favor is not cash), `FX-STMT-MISMATCH-01`
(incomplete timing list), `FX-BAL-OVERRESERVED-01`.

**Implemented as calculator fixtures in 5A**, in
`backend/src/test/java/com/keenti/finances/domain/model/PlanningPreviewFixtures.java`:
`FX-HORIZON-CASH-01` (ex.1), `FX-HORIZON-BOXFUNDED-01` (ex.2),
`FX-HORIZON-BOXSHORT-01` (ex.3), `FX-HORIZON-STMT-NEUTRAL-01` (ex.4),
`FX-HORIZON-FUTURE-RECORDED-01` (ex.11), `FX-HORIZON-INCOME-OPTIN-01` (ex.8),
`FX-HORIZON-PARTIAL-01` (ex.9). These are new deterministic fixtures, not live development records.

## Delivery

D5 approval is recorded above. No schema migration is needed for this preview.
5A is implemented; the API and UI remain separate slices.

| Slice | Dependencies | Result and acceptance |
|---|---|---|
| 5A — calculation and fixtures | D5 approval; existing 0B fixtures and D1/D2 semantics | Pure decimal calculation and fixtures above; assert `N'−B'=U'`, receipt effects on both totals, aggregate Box capacity, negative baselines, recorded/future-dated/MSI exclusion, and local-date boundaries. |
| 5B — preview reads | 5A; existing 2B Box contract | Endpoint validates ownership, eligible receipts and exact money; reads all Boxes including no-plan Boxes and all relevant statements; consistent snapshot and explicit independent failure states. No financial writes. |
| 5C — scenario UI | 5B; Phase 4 availability and navigation | Manual dated costs, explicit Box funding, opt-in receipts, review confirmations and separate statement timing. Browser checks cover partial/unavailable results, refresh/reconfirmation, lost-draft disclosure, overdue statements, long lists, keyboard and narrow screens. |

Ship the feature only when all three slices pass. Verify with deterministic fixtures,
not incidental shared development balances. Reuse current Box Plan period/revision
context from 2B without forecasting future periods or turning top-up suggestions into
costs. Acceptance includes failed reads, PAID-without-Transaction receipts, duplicates,
foreign IDs, and a cost recorded between previews. Tests assert behavior and invariants,
not copied wording. Persisted plans and account liquidity forecasts remain separate
future decisions.

## Source anchors

- [`CONTEXT.md`](../../CONTEXT.md); [`balance-presentation.md`](balance-presentation.md) (D1); [`obligation-status.md`](obligation-status.md) (D2); [`../features/dashboard.md`](../features/dashboard.md); [`../features/box-plan-integration.md`](../features/box-plan-integration.md).
- ADR-[0010](../adr/0010-transaction-subscription-link.md), [0019](../adr/0019-manual-per-subscription-billing-trigger.md), [0020](../adr/0020-boxes-are-internal-allocations.md), [0021](../adr/0021-box-plans-are-user-controlled-guidance.md), [0022](../adr/0022-financial-accounts-and-transfers.md), [0023](../adr/0023-debts-are-bidirectional.md).
- `application/service/TransactionService.java:288,390` (only Box funding/distributions reject future dates); `infrastructure/adapter/out/persistence/PanacheFinancialAccountRepository.java:155,186,226` (balances include all non-deleted Transactions; `getBalanceAt` is the only date-filtered read); `domain/model/SavingGoalRevision.java` (`targetDate`); `domain/model/Debt.java` (no due date); `CreditStatement.java` (`dueDate`, `officialBalance`, `paidAmount`).
