# Dashboard composition and provenance

Phase 4. The dashboard answers five questions in order, from one composed read
model. It introduces no accounting formula, no forecast, and no new domain term:
every figure is copied from the service that already owns it, and the sections
below name which one.

## The endpoint

`GET /api/dashboard/overview?year=YYYY` — additive; `GET /api/dashboard/summary`
is unchanged and still serves its existing callers.

`year` scopes the **history** section only and accepts `current` (the User's own
calendar year, from their configured time zone) or a four-digit year. Anything
else answers 400 rather than guessing a year. If the User's calendar cannot be
resolved, `current` leaves history unavailable while other sections still load;
an explicit year can still load recorded history. The current-position totals are
all-time by definition, so moving between years never changes one of them.

The response is:

```jsonc
{
  "today": "2026-09-07",          // the User's calendar day, or null
  "timeZone": "America/Mexico_City",
  "position":  { "status": "ok", "reason": null, "data": { … } },
  "attention": { "status": "ok", "reason": null, "data": { … } },
  "plans":     { "status": "ok", "reason": null, "data": { … } },
  "expected":  { "status": "ok", "reason": null, "data": { … } },
  "history":   { "status": "ok", "reason": null, "data": { … } }
}
```

### Partial availability

Each section is computed in isolation. A section that could not be computed
arrives as `{"status":"unavailable","reason":"error","data":null}` — **with no
data at all**. Nothing substitutes a zero, an empty list, or a reassuring status
for a read that failed, because `$0.00` and "nothing due" are claims about the
User's money. The page renders each unavailable section with its own Retry and
keeps its neighbours' figures.

Three sections additionally carry a `partial` flag for per-item failures inside an
otherwise successful section — one unreadable Credit Financial Account leaves
the other statements listed, and the section says it may be incomplete rather
than implying the unread card owes nothing. `attention.statementsTruncated`,
`attention.billingTruncated`, `expected.debtsTruncated` and
`expected.contributionsTruncated` say the *list* was bounded; where a total sits
beside one it is still computed over everything. Billing cursors are ordered by
cursor date — unreadable ones first — before the cap, so the furthest-behind
survive it; which ones are actually behind needs the User's calendar day and so
cannot be known server-side.

### No writes

Every part of the endpoint is a read. It generates no billing, advances no
generation cursor (ADR-0019), creates no Transaction, and moves no money between
Boxes or Financial Accounts. The single exception is the idempotent lazy Box Plan
period evaluation that ADR-0021 already performs on *every* plan read: it records
plan progress and moves no money.

### Cost

One request replaces the page's former fan-out — an account list, then a
credit-settings and a confirmed-statement read per Credit Financial Account —
and reaches data that fan-out could not see at all. The frontend fetches no
per-item history: no Transaction list, no Box movement log, no per-Subscription
payment history is requested to compose the dashboard.

Known per-item work inside the endpoint: one statement-estimate pair of queries
per confirmed statement (the same cost `GET /api/accounts/{id}/credit-statements`
has today) and one plan evaluation per Box with an active plan. Pending member
contributions use one user-scoped projection query, excluding paid records and
trashed Subscriptions. Debt paid totals are summed for every Debt in a single
grouped query rather than one query per Debt. Account rows, billing cursors, and
active Box plans intentionally remain complete lists; they are not paginated
and may produce a long dashboard for users with many accounts or plans. These
are current summaries, not full transaction or payment histories.

## 1 — Current position

| Field | Source |
|---|---|
| `netBalance` | `FinancialAccountRepository.getTotalBalance()` when tracking is active, otherwise `TransactionRepository.getNetBalance()` — the same choice `DashboardService` makes |
| `inBoxes` | `BoxRepository.getTotalBalance()` |
| `availableToSpend` | `netBalance − inBoxes` |
| `moneyHeld` | sum of signed balances of active non-`CREDIT` Financial Accounts |
| `creditDebt` | sum of the negative `CREDIT` balances, as a positive magnitude |
| `creditInFavor` | sum of the positive `CREDIT` balances |
| `availableCredit` | `Σ max(creditLimit + balance, 0)` over cards with a configured limit |

Credit debt and credit in the User's favour are **separate sums**, not one signed
figure: they are different situations, and netting them would let an overpaid
card mask another that is owed.

`moneyHeld`, `creditDebt`, `creditInFavor` and `availableCredit` are `null` while
Financial Account tracking is off. Before activation there are no signed account
balances to split, and decision D1 forbids guessing a breakdown for a Net Balance
that is recorded income minus expenses. `null` says "there is no breakdown";
`0` would claim the User holds nothing.

`availableCredit` is `null` when no card has a configured limit — `$0.00` would
claim no capacity is left, which is a different statement. It is limit-derived
capacity and enters none of the totals above.

`creditLimitsPartial` withholds the capacity total and offers Retry when any
credit-settings read fails; known position figures remain visible.

Which formula produced the Net Balance is still explained from the authoritative
`active` boolean on `GET /api/accounts/status`, read by the layout (decision D1).
The overview never becomes a second authority for it.

## 2 — Needs attention

The section ships **facts**, not badges: confirmed statement amounts and due
dates, generation cursors, and the account balances and limits the page derives
overdrawn and over-limit alerts from. Labels are derived in the frontend against
the one instant the layout captured in the User's own time zone
(`obligationToday`), so the dashboard and the Credit Financial Account page
cannot disagree about which calendar day it is.

- **Confirmed statements** use `outstandingBalance` = official balance minus
  allocated payments (truth table C). A statement that is covered and unflagged
  is omitted; one that is outstanding *or* carries `reconciliationMismatch` is
  listed. The obligation is stated separately and never subtracted from Net
  Balance: the purchases behind it already moved it.
- **Billing generation** is the cursor only (truth table A). It is not evidence
  that a provider charged anything or that anyone is late, and reading it never
  advances it. Generation stays on the Subscription's own page.
- **Excess reservations and a negative recorded position** are derived from the
  position figures by the existing `availableToSpendExplanation`, which tells the
  two apart and only offers a withdrawal when a Box actually holds money.
- **Reconciliation mismatch** is an independent review notice. It survives both a
  covered payment state and the attention-slot priority sort, so a mismatch on a
  fully paid statement is still shown.

## 3 — Your plans

Active Saving Goals and Spending Budgets, with the figures their own services
publish — target, remaining, progress, current commitment (which already includes
carried arrears), desired balance, suggested top-up. Nothing is recomputed here;
reproducing a plan's arithmetic on the dashboard is how two screens start
disagreeing about the same plan. See `box-plan-integration.md`.

Suggestions are guidance. Nothing is deposited or allocated until the User
confirms it, and the section says so once rather than implying automation by
placing an amount next to a button.

`reservedInPlannedBoxes` is money already inside `inBoxes`. It is context for the
plans, not a second claim on the same money. `boxesWithoutActivePlan` counts
Boxes whose plan list loaded and is genuinely empty; a Box whose plan could not
be read sets `partial` instead, because "no plan" invites creating one and that
is the wrong offer for a plan we failed to understand.

## 4 — Money expected

Recorded as owed to the User and **not received**. None of it is added to Net
Balance, In Boxes or Available to Spend.

- Outstanding `ACTIVE` Debts with Direction `INGRESS` (ADR-0023): `totalAmount − Σ Debt Payments`, listed only when
  something is still owed. A Debt whose payments already cover it is excluded
  whatever its stored status says. `EGRESS` Debts describe what the User owes
  and are excluded from money expected; the two directions are never netted.
- Positive-amount `PENDING` Payment Records belonging to a Subscription Member.
  Zero-amount rows are omitted because this section counts money still owed,
  rather than every open Payment Record. They stay
  *awaiting* however old the billing date is: no contribution due date or grace
  period is agreed anywhere in the contract, so "late" would be invented policy
  (decision D2).
- A Personal Subscription's own record (`memberId = null`) is the Owner's own
  charge, not money another person owes, and never appears here.
- A `PAID` record with no linked Transaction is still received. A missing link is
  a missing link, not an unpaid contribution.

If pending contributions cannot be read, `contributionsAvailable` is false and
its total is null. Any successfully read Debts remain visible; the section does
not claim that expected money is zero.

Every row carries the id of the record it came from — `debtId`,
`paymentRecordId`, `subscriptionId` — and links to it, so a total can be traced.

## 5 — History

The selected year's `totalIngress`, `totalEgress` and twelve `monthly` entries,
from `TransactionRepository.findMonthlySummary(year)`, with the same annual
sums as `DashboardService`. This read is independent of account and Box balances. The annual charts and trend
are unchanged.

## Empty and new users

The existing layout sends users who require account setup to Financial Accounts.
The dashboard setup card is a fallback when that layout status is unavailable
but the overview confirms setup is required. Initialized users with genuinely
empty sections see their real zero position, links to create a Box plan, and
empty history. A zero balance alone never classifies someone as a new user.

## Verification

The synthetic `FX-DASH-NEG-01` fixture is the source of truth for the acceptance
figures, and the manifest, the fixture body and the assertions all read the same
`expected` block so none can drift:

```
4,120.50 held + 55.50 credit in favor = 4,176.00 Net Balance
4,176.00 − 5,300.00 In Boxes          = −1,124.00 Available to Spend
9,000.00 limit + 55.50                = 9,055.50 available credit (capacity only)
310.25 confirmed statement payment — separate, subtracted from nothing above
```

```bash
cd frontend
bun test tests/dashboard-overview.test.js
bun run tests/fixtures/server.ts FX-DASH-NEG-01           # 127.0.0.1:8099, read-only
```

Backend coverage lives in `DashboardOverviewResourceTest`: aggregation and the
arithmetic above, the statement that is never subtracted twice, expected receipts
including the excluded `memberId = null` record, cross-user isolation, the
year-scoping rule, and the assertion that repeated reads generate no billing,
advance no cursor and move no money. Those are `@QuarkusTest` cases and need a
database, so they run in CI.

## Source anchors

- `CONTEXT.md`: Net Balance, In Boxes, Available to Spend, Box Plan, Debt Direction.
- ADR-0019 (manual billing generation), ADR-0021 (plans are guidance),
  ADR-0022 (signed ledger and statement snapshots), ADR-0011/0014 (user scope).
- `docs/decisions/balance-presentation.md` (D1), `obligation-status.md` (D2).
- `backend/.../application/service/DashboardOverviewService.java`.
- `frontend/src/routes/+page.server.ts` and `src/lib/components/dashboard/`.
