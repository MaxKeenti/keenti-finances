# Synthetic verification fixtures — Slices 0A and 0B

Every value in `scenarios.ts` is invented for verification. No fixture reads, seeds, or modifies development or production data, and none of the figures describe a real account. `scenarios.ts` is authoritative: if a value changes there, this document follows it rather than being maintained independently.

| File | Purpose |
|---|---|
| `scenarios.ts` | Scenario data, stable IDs, and expected values |
| `backend.ts` | In-process `fetch` replacement with failure injection |
| `clock.ts` | Fixed instants, time zones, and a scoped clock |
| `server.ts` | Local HTTP fixture backend for browser verification |
| `harness-routes.ts` | Shell routes every authenticated page needs |

## Setup and reset

Nothing is seeded and nothing needs cleaning up. Scenarios are in-memory constants and `loadScenario` returns a deep copy, so a test that mutates its data cannot affect another. "Reset" is constructing a new fixture backend, or calling `reset()` on one.

```bash
cd frontend
bun test tests/fixtures.test.js                     # the harness itself
bun test tests/navigation-and-boxes-overview.test.js # slice 2A and the 0B values
bun test                                             # everything
```

## Route keys

A route key is `"<METHOD> <path>"`, e.g. `GET /api/boxes`. A scenario may also declare the query-bearing spelling, e.g. `GET /api/boxes?archived=true`; the more specific key wins, and the bare path stays the default. This is how the Boxes overview's active and archived lists stay distinguishable. A route no scenario declares throws in the in-process backend and answers 404 in the browser server, so an unplanned request fails visibly instead of quietly returning an empty list.

## Slice 0A scenarios

Amounts are MXN. `Available to Spend = Net Balance − In Boxes` in every balance scenario.

| ID | Scenario | Key expected values | Supports |
|---|---|---|---|
| `FX-TRACK-INACTIVE-01` | Tracking not configured | `active: false`, `setupRequired: true`, account net 0 | 1A-balance |
| `FX-TRACK-ACTIVE-01` | Tracking active, healthy position | Net 2,500.75; In Boxes 500.00; Available 2,000.75 | 1A-balance, 1A-loader |
| `FX-BAL-ZERO-01` | Legitimate zero | Net 0; In Boxes 0; Available 0 | 1A-loader (zero vs failure) |
| `FX-BAL-OVERRESERVED-01` | Positive net, excessive reservations | 3,200.00 + 40.00 = 3,240.00; In Boxes 4,000.00; Available −760.00 | 1A-balance, 2C |
| `FX-BAL-NEGNET-01` | Negative Net Balance | 150.00 + (−900.00) = −750.00; Available −750.00 | 1A-balance |
| `FX-BOX-EMPTY-NEGAVAIL-01` | Empty Box, negative Available | Box 9205 at 0.00; Net 1,900.00; In Boxes 2,600.00; Available −700.00 | 1A-balance, 2C |
| `FX-STMT-MISMATCH-01` | Confirmed-statement mismatch | Statement 9301: official 1,250.00, mismatch 125.50, due 2026-09-20 | 1A-balance, 3A |
| `FX-SUB-SHARED-POPULATED-01` | Shared subscription with data | Subscription 9401, cost 299.00, 2 members, 3 payments, 1 linked income | 1A-loader, 1B, 3B |
| `FX-SUB-EMPTY-01` | Genuinely empty subscription | Subscription 9402, cost 149.00, 0 members, 0 payments, HTTP 200 | 1A-loader |
| `FX-DEBT-000-01` / `-010-` / `-100-` | Debt progress 0 / 10 / 100% | Debts 9901–9903, total 1,000.00 | 1B |
| `FX-DEBT-RECEIVABLES-01` | Money owed across debtors | Debts 9904–9908; total outstanding 2,050.00 over 4 active debts and 3 debtors; Contact 9504 owes 1,200.00, Contact 9503 owes 750.00 across two debts, debt 9907 has no Contact and owes 100.00; debt 9906 is settled and excluded | 3C |
| `FX-DATE-BOUNDARY-01` | User time-zone day boundary | See the clock table below | 1D (with D2), 3A, 3B |
| `FX-LONGNAME-01` | Very long overdrawn account name | Account 9111, balance −1,234.56 | 1C |

## Slice 0B scenarios

Prerequisites for 2A/2B, 3A/3B, and Phase 4.

| ID | Scenario | Key expected values | Supports |
|---|---|---|---|
| `FX-PLAN-GOAL-ACTIVE-01` | Active Saving Goal | Box 9210, plan 9251 `ACTIVE`; balance 3,000.00 of target 12,000.00; remaining 9,000.00; 25%; commitment 1,500.00 | 2A plan status, 2B |
| `FX-PLAN-GOAL-OVERDUE-01` | Overdue Saving Goal | Box 9211, plan 9252 `OVERDUE`; balance 1,200.00 of 5,000.00; remaining 3,800.00; arrears 800.00; target date 2026-08-31 | 2A plan status, 2B |
| `FX-PLAN-GOAL-COMPLETED-01` | Completed Saving Goal | Box 9212, plan 9253 `COMPLETED` at 4,500.00; **no active plan remains** | 2A no-plan state, 2B history |
| `FX-PLAN-BUDGET-UNDER-01` | Underfunded Spending Budget | Box 9213, plan 9254 `ACTIVE`; desired 2,000.00, balance 450.00, suggested top-up 1,550.00, funded spending 1,550.00 | 2A, 2B |
| `FX-BOX-NOPLAN-01` | Unplanned Box beside an unreadable one | Box 9214 has an empty plan list; Box 9215's plan route is the one a test or `FIXTURE_FAIL` makes fail. Net 2,400.00; In Boxes 900.00; Available 1,500.00 | 2A (absent vs unavailable) |
| `FX-SUB-SHARED-OWNER-PARTIAL-01` | Shared subscription, Owner participates | Subscription 9406, cost 600.00 split three ways at 200.00; expected contributions 400.00, collected 200.00, outstanding 200.00; own share 200.00 | 3B |
| `FX-SUB-SHARED-MIDDLEMAN-PARTIAL-01` | Shared subscription, middleman mode | Subscription 9407, cost 600.00 split between two Members at 300.00; expected 600.00, collected 300.00, outstanding 300.00; own share 0.00 | 3B |
| `FX-SUB-LIST-01` | Subscriptions overview | Subscriptions 9408 (Shared monthly 600.00, 2 members at 200.00), 9409 (Shared monthly 150.00, no members, middleman), 9410 (Personal yearly 1,200.00); monthly price equivalent 850.00, yearly 10,200.00 | 3B |
| `FX-CREDIT-INFAVOR-STMT-01` | Credit in favor with an unpaid statement | Account 9112 at +85.00, limit 12,000.00, available credit 12,085.00; statement 9303 outstanding 640.00 due 2026-09-20; Net 1,085.00 | 3A |
| `FX-DASH-NEG-01` | Positive Net Balance, excessive reserves | See below | Phase 4 |

### `FX-DASH-NEG-01`

| Element | Expected value (MXN) |
|---|---|
| Asset Financial Account balances (money held) | 4,120.50 |
| Credit Financial Account signed balance (credit in your favor) | 55.50 |
| Net Balance | 4,176.00 |
| In Boxes | 5,300.00 |
| Available to Spend | −1,124.00 |
| Unpaid confirmed statement payment | 310.25 |
| Credit limit | 9,000.00 |
| Available credit (limit-derived, excluded from the totals above) | 9,055.50 |

Arithmetic the fixture asserts: `4,120.50 + 55.50 = 4,176.00`; `4,176.00 − 5,300.00 = −1,124.00`; `9,000.00 + 55.50 = 9,055.50`. The 310.25 confirmed statement payment is a separately identified obligation and is never subtracted from Net Balance. Available credit is limit-derived capacity and never enters money held, Net Balance, In Boxes, or Available to Spend. Its figures are deliberately distinct from `FX-BAL-OVERRESERVED-01`, which does not substitute for it.

### Subscriptions overview verification

`FX-SUB-LIST-01` serves the overview's three reads (`/api/subscriptions`,
`/api/categories`, `/api/contacts`) plus the member lists of its two Shared
Subscriptions. Open `/subscriptions` to see the price-equivalent card, the
member-less **Add members** action, and — with
`FIXTURE_FAIL='GET /api/subscriptions=500'` — the unavailable list that shows
no totals instead of `$0.00`. `FIXTURE_FAIL='GET /api/subscriptions/9408/members=503'`
shows an unavailable member count beside a Subscription whose member list loaded.

### Reserved ID ranges

Accounts `91xx`, boxes `92xx`, credit statements `93xx`, subscriptions/members/payments `94xx`, contacts `95xx`, transactions `96xx`, debt payments `97xx`, categories `98xx`, debts `99xx`. Slice 0B additionally uses box plans `925x`, Saving Goal periods `926x`, and plan revisions `927x`.

## Clock and time-zone inputs

| Clock | Instant (UTC) | Time zone | User calendar day |
|---|---|---|---|
| `MEXICO_CITY_MIDDAY` | 2026-09-07T18:00:00Z | America/Mexico_City | 2026-09-07 |
| `MEXICO_CITY_EVENING` | 2026-09-08T04:30:00Z | America/Mexico_City | 2026-09-07 |
| `TOKYO_SAME_INSTANT` | 2026-09-08T04:30:00Z | Asia/Tokyo | 2026-09-08 |

`withFixtureClock` pins `Date.now()` for the duration of a callback and always restores the real clock, including on throw. It is a test helper: a browser pointed at the fixture server renders against the machine's real wall clock, so any date-boundary check there must come from the fixture's own dates.

## Failure injection

Failures are injected in the harness only. No live service is disrupted and no request leaves the process.

```ts
const backend = createFixtureBackend('FX-BOX-NOPLAN-01', {
  failures: { 'GET /api/boxes/9215/plans': { kind: 'status', status: 500 } },
});
```

- `{ kind: 'status', status }` resolves with an error response; `{ kind: 'unreachable' }` rejects like a network failure (in-process only).
- `failRoute` / `healRoute` toggle a route mid-test, for Retry behavior.
- `backend.requests` records every call, and `assertNoUndeclaredRoutes()` proves an asserted outage is the injected one.

## Browser verification

```bash
cd frontend
bun run tests/fixtures/server.ts FX-PLAN-BUDGET-UNDER-01            # 127.0.0.1:8099
FIXTURE_FAIL='GET /api/boxes/9215/plans=500' \
  bun run tests/fixtures/server.ts FX-BOX-NOPLAN-01                 # unavailable plan state
```

Then, in a second shell:

```bash
cd frontend
BACKEND_URL=http://127.0.0.1:8099 TEST_AUTH_BYPASS=true bun run dev
```

`TEST_AUTH_BYPASS` is the repository's existing non-production local flag in `src/hooks.server.ts`; it is refused when `NODE_ENV=production`. The server binds `127.0.0.1` only, and because `BACKEND_URL` points at it, the browser session cannot reach or modify shared development data.

Known limits: the fixture server is read-only, so POST/PUT/DELETE actions answer 404 unless a slice adds them; each scenario declares only the routes its acceptance cases need, and a page reading something else shows the loader's error state and logs the missing key; `FIXTURE_FAIL` takes HTTP error statuses only (400–599) and is fixed at startup, so "healing" a route means restarting without it.

## Deliberately out of scope

- Obligation statuses (pending/overdue/paid derivations) await decision D2; the fixtures supply dates and records, not statuses.
- No Flyway migration, backend seed, or deployed-environment data was created or changed.

### Recording fixture

`FX-RECORDING-01` extends the empty-Box/negative-Available scenario with 31 synthetic transactions and account/category reads. Searching `needle` finds the last transaction before pagination. `/transactions?expenseFromBox=9205` opens an expense draft with the empty Box selected, without saving. Remove its zero allocation to record an unfunded expense: 100.00 changes projected Available from −700.00 to −800.00. Request-level tests cover no funding, 60.00 funding, and 100.00 funding as a single Transaction POST (never a second withdrawal). The HTTP fixture server remains read-only.

The receivables fixture also supplies Contacts, a long-name Trash item, and debt 9904 with payment 9708 linking to transaction 9612 for read-only browser navigation checks.

### Credit detail verification

`FX-CREDIT-INFAVOR-STMT-01` also supplies the account detail, empty activity,
empty archived-account list, MSI list, and the next statement estimate. Open
`/accounts/9112` to verify the positive credit position alongside the unpaid
confirmed statement, then open and cancel the contextual card-payment form.
The fixture server remains read-only; it cannot record a Transfer.
