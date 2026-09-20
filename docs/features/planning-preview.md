# Planning preview implementation

[D5](../decisions/planning-horizon.md) was approved on 20 September 2026.
Slice **5A** implements the pure calculator and deterministic fixtures. There is no
route, endpoint, persistence change or visible preview yet. Slices 5B and 5C must
pass before the feature is exposed.

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
Output amounts have two decimal places. Individual costs/receipts are bounded at
9,999,999.99; aggregate balances are not subject to that per-item limit.

- `COMPLETE`: valid inputs, essentials reviewed, and every cost confirmed unrecorded.
- `PARTIAL`: valid subtotal with explicit missing review confirmations.
- `UNAVAILABLE`: invalid baseline/zone or business-invalid row, with `projected=null`.

Reason codes include zero-based cost/receipt indices where relevant. A cumulative
Box-capacity conflict names the Box and shortfall. Funding without a Box is invalid;
omitted Box funding is zero. No row is dropped to manufacture a valid result.
Structural misuse (null row/list, invalid receipt identity, over 50 rows) throws
`IllegalArgumentException`; no HTTP response handling exists in this slice.

5B maps business failures to the accepted 200 envelope and structural failures to
4xx, validates transport fields (including horizon length and description size),
and adds independently available timing and undated-Debt sections. It must not
serialize this domain result as a substitute for the full D5 response. Fresh,
consistent reads and all identity/eligibility checks remain 5B acceptance gates.

## Fixtures and verification

`PlanningPreviewFixtures` contains all seven `FX-HORIZON-*` calculator fixtures from
D5, with fixed dates and invented money. Calculator tests also use the baseline
shapes from `FX-DASH-NEG-01` and `FX-BOX-NOPLAN-01` in the existing frontend catalog.
They cover arithmetic, cumulative funding, full/partial funding, opt-in receipts,
negative balances, missing reviews, invalid money/IDs/dates, immutable collections,
and calendar boundaries across time zones, leap years, year end and DST.

The statement-neutral and future-recorded fixtures pass an already-resolved
baseline and no new cost. They establish the empty-scenario calculation contract;
they do **not** prove storage selection excludes those records. Slice 5B must add
integration fixtures for those exclusions, both tracking branches, archived accounts,
PAID-without-Transaction records, foreign IDs, failed reads, complete statement
pagination and no financial writes. Slice 5C owns browser/keyboard/narrow-screen
checks, draft loss disclosure, refresh/reconfirmation, and independent section states.
