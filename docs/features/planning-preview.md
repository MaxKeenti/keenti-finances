# Planning preview implementation

[D5](../decisions/planning-horizon.md) was approved on 20 September 2026.

| Slice | Status |
|---|---|
| 5A — calculator and fixtures | implemented |
| 5B — `POST /api/planning/preview` | implemented (API only) |
| 5C — scenario interface | pending |

No route, navigation entry or visible preview exists yet; the endpoint is not
linked from the interface until 5C passes. No migration, dependency or deployment
change was needed.

## Calculator boundary

`PlanningPreviewCalculator.calculate` receives a captured instant, an IANA zone,
a baseline with every active Box balance, manual costs, selected resolved receipts,
and review confirmations. It performs no reads or writes. Baseline collections and
returned results are immutable snapshots.

The caller must read Net Balance through the existing tracking-mode branch and
supply all active Boxes, including those without plans. The calculator checks that
their sum equals In Boxes; missing/inconsistent balances yield unavailable, never
an empty balance. It accepts a negative Net Balance or Available to Spend.

Only new unrecorded costs enter the expense list. Statements, recorded Transactions
(including future-dated ones), MSI schedules and Box Plan top-up suggestions have no
input channel. This boundary does not detect a User incorrectly entering an actual
purchase again; manual confirmation and refresh behavior remain required by D5.

For receipts, slice 5B must reload ownership, eligibility and amount from storage:
ACTIVE INGRESS Debt remaining amounts and PENDING Subscription Member Payment Record
amounts on non-deleted Subscriptions only. PAID records, including those without
linked Transactions, are ineligible. The calculator cannot infer these facts from an
amount; its `Receipt` is an already-resolved input, not a request body. It checks
positive cent amounts, scenario dates and duplicate `(kind, id)` selections.

## Results and validation

The calculation uses exact `BigDecimal` cents. Equivalent values such as `1.000`
are accepted; fractional cents are rejected rather than rounded into valid input.
Output amounts have two decimal places. Individual costs, receipts and Box funding
are bounded at 9,999,999.99 by one shared predicate
(`PlanningPreviewCalculator.validAmount`); aggregate balances are not subject to
that per-item limit. The bound is checked on magnitude before any arithmetic, so a
value such as `1e999999999` (which has no fractional cents) is refused as
`INVALID_AMOUNT`/`INVALID_FUNDING` without being expanded. Funding above its cost
is `FUNDING_EXCEEDS_COST` and is not added to the Box total, so it never produces a
`BOX_CAPACITY_EXCEEDED` shortfall of its own.

- `COMPLETE`: valid inputs, essentials reviewed, and every cost confirmed unrecorded.
- `PARTIAL`: valid subtotal with explicit missing review confirmations.
- `UNAVAILABLE`: invalid baseline/zone or business-invalid row, with `projected=null`.

Reason codes include zero-based cost/receipt indices where relevant. A cumulative
Box-capacity conflict names the Box and shortfall. Funding without a Box is invalid;
omitted Box funding is zero. No row is dropped to manufacture a valid result.
Structural misuse (null row/list, invalid receipt identity, over 50 rows) throws
`IllegalArgumentException`; no HTTP response handling exists in this slice.

5B found no arithmetic defect. For API integration it made `window(...)` public
so timing uses the exact calendar rows are validated against, and added the
receipt-resolution reasons `RECEIPT_NOT_FOUND`, `RECEIPT_INELIGIBLE` and
`RECEIPT_UNAVAILABLE` (never produced by the calculator itself).

## Preview API (5B)

`PlanningPreviewResource` → `PlanningPreviewService` → `PlanningSnapshotReader`.

**Request validation.** Malformed JSON, a missing field, an unknown `recordKind`,
`horizonDays` other than 30, more than 50 costs or receipts, or a description over
200 characters is a 400 with no preview; a missing user header is a 401. Money
values are *not* validated structurally: a zero, negative, over-limit or
fractional-cent amount or funding is a business-invalid row (`INVALID_AMOUNT`,
`INVALID_FUNDING`) in a 200 envelope, identified by index. Receipts carry no
amount and the request carries no baseline; unknown JSON properties are ignored
like every other endpoint, and never read.

**Order of work.** One instant is captured and the User's zone resolved (an
unusable zone is `ZONE_UNAVAILABLE`, never UTC). ADR-0021 lazy evaluation then runs
for every active Box Plan in the plan services' own transactions, so it is
committed before any balance is read; a failure is the note
`PLAN_EVALUATION_INCOMPLETE`, since evaluation changes no balance. Then three
sections are read.

**Snapshots and isolation.** Each section is a separate `@Transactional(REQUIRES_NEW)`
method whose first statement is `SET TRANSACTION ISOLATION LEVEL REPEATABLE READ,
READ ONLY` (`PostgresConsistentReadScope`), and which is always marked rollback-only.

- *Why REPEATABLE READ:* PostgreSQL's default READ COMMITTED gives each statement a
  new snapshot, so Net Balance, In Boxes, per-Box balances and receipt amounts
  could straddle a concurrent commit. One snapshot per section makes them agree.
- *Why READ ONLY + rollback:* the database itself refuses writes, and nothing a
  section does can be committed.
- *Why three transactions, not one:* after any failed statement PostgreSQL aborts
  the whole transaction, and Hibernate marks it rollback-only. A timing-read
  failure sharing the baseline's transaction would therefore poison the projection.
  Separate transactions keep sections independent; within a section, reads stop at
  the first failure and the section is reported unavailable. The trade-off is that
  sections may be read at slightly different instants — acceptable because the
  timing list and undated Debts are never netted into the projection.
- The rollback relies on the `@Transactional` interceptor, which ends a
  rollback-only transaction quietly (`QuarkusTransaction.run` would throw).

| Section | Reads | On failure |
|---|---|---|
| Projection | tracking branch (`getTotalBalance` or `getNetBalance`), every active Box including no-plan Boxes, the dashboard's In Boxes total, credit in favor, and selected receipts | baseline failure: `BASELINE_UNAVAILABLE`, `baseline` and `projected` null; receipt failure: baseline kept, `RECEIPT_UNAVAILABLE`, `projected` null. `CREDIT_IN_FAVOR_IN_BASELINE` is noted only when a calculated `baseline` is returned (not when In Boxes disagrees with the Boxes) |
| Timing | all non-archived credit accounts, their full uncapped statement lists, mismatch estimates, and current/previous period estimates | `unavailable` with `READ_FAILED`, empty lists — never a shortened list |
| Undated Debts | ACTIVE Debts of both Directions with remaining > 0 | `undatedDebts` null, `undatedDebtsStatus: "unavailable"` |

**Receipts.** Duplicate `(recordKind, recordId)` selections are `DUPLICATE_RECEIPT`
and looked up once. Debts come from the user-scoped, trash-filtered list: ACTIVE,
INGRESS and remaining (total − recorded payments) a valid preview amount; anything
else owned is `RECEIPT_INELIGIBLE`. Payment Records come from a native query
restricted to the User's Subscriptions not in the trash: PENDING, with a
Subscription Member and a valid preview amount. PAID records are ineligible whether
or not a Transaction is linked. "Valid preview amount" is the calculator's own
predicate (positive, ≤ 9,999,999.99, cents): storage allows larger Debts, and one
the preview cannot carry is `RECEIPT_INELIGIBLE`, never the User's
`INVALID_AMOUNT`. Unknown, non-positive, trashed-Debt, trashed-Subscription and
other-User IDs all produce the same `RECEIPT_NOT_FOUND`. Any resolution problem makes the projection
unavailable; the calculator's resolved-row indices are mapped back to request rows.

**Timing.** Confirmed statements with outstanding > 0 are `overdue` (due before
today, however old) or `dated` (due inside the window); later ones are omitted.
`partial` reasons: `STATEMENT_DUE_DATE_MISSING`, `RECONCILIATION_MISMATCH` (row also
flagged), `STATEMENT_SCHEDULE_MISSING`, and `UNCONFIRMED_STATEMENT` when the open or
last closed period has no confirmed statement, a positive estimate (MSI included)
and a due date on or before the window end (including overdue periods).
Unconfirmed estimates appear in `estimates` as
context and are never obligations. Tracking off is `notApplicable`
(`TRACKING_INACTIVE`), checked before the zone. Capacity is read nowhere.

**Response.** `generatedAt`, `timeZone`, `window`, `baseline` (`netBalance`,
`inBoxes`, `availableToSpend`, `source: accounts|transactions`, `creditInFavor` —
inside Net Balance, not cash), `projected` (totals and `perBox` with current and
projected balances), `status` (`complete|partial|unavailable`), `missingInputs`
(reason with `itemIndex`, `receiptIndex`, `boxId`, `shortfall`), `includedReceipts`
(server amounts, *if received*), `timing` (`status`
`complete|partial|unavailable|notApplicable`, `dated`, `overdue`, `estimates`,
`reasons`), `undatedDebts` (one row per Debt with its Direction, no totals),
`undatedDebtsStatus` and `notes` codes. `generatedAt` is a timestamp, not a
revision.

## Fixtures and verification

`PlanningPreviewFixtures` contains all seven `FX-HORIZON-*` calculator fixtures from
D5, with fixed dates and invented money. Calculator tests also use the baseline
shapes from `FX-DASH-NEG-01` and `FX-BOX-NOPLAN-01` in the existing frontend catalog.
They cover arithmetic, cumulative funding, full/partial funding, opt-in receipts,
negative balances, missing reviews, invalid money/IDs/dates, immutable collections,
and calendar boundaries across time zones, leap years, year end and DST.

The statement-neutral and future-recorded fixtures pass an already-resolved
baseline and no new cost. They establish the empty-scenario calculation contract;
they do **not** prove storage selection excludes those records. The 5B storage tests
below do.

5B verification:

- `PlanningPreviewResourceTest` (`@QuarkusTest`, real PostgreSQL): baseline equals
  the dashboard's and already contains a recorded future-dated EGRESS, a full MSI
  purchase and credit in favor, with no capacity; both tracking branches;
  no-Box/no-plan Boxes; server-side receipt amounts ignoring client fields;
  ineligible, PAID-without-Transaction, Owner's-own, over-bound Debt, trashed
  (not found, like unknown), foreign, unknown and duplicate receipts; an
  extreme-exponent `boxAmount` refused quickly with no shortfall; structural 4xx versus business 200 envelopes, window
  endpoints, cumulative Box capacity and partial subtotals; invalid zone; 54
  statements (51 overdue, beyond the dashboard's cap) plus window boundaries;
  unconfirmed and mismatched periods; bidirectional Debts; no writes (billing,
  cursor, Transactions, Debt payments, Box history) and a cost recorded between
  previews appearing in the next one.
- `PostgresConsistentReadScopeTest`: REPEATABLE READ + read-only on a real
  transaction, writes refused, a failed section not poisoning the next one, and
  a concurrent commit between queries becoming visible only in the next snapshot.
- `PlanningSnapshotReaderTest` and `PlanningPreviewServiceTest` (stub ports): each
  section's failure in isolation, timing classification, eligibility rules, plan
  evaluation order, and receipt index mapping. Stubs prove composition, not storage.

Not covered by an automated test: a *real* database failure inside the preview
endpoint (only the probe and stubs simulate it), archived-account statements
(excluded by the same `findAll(false)` rule as the dashboard). Slice 5C owns browser/keyboard/narrow-screen checks,
draft loss disclosure, refresh/reconfirmation, and independent section states.
