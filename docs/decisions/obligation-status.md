# D2 — Obligation status and calendar-day contract

Status: accepted as the implementation starting point following the product owner’s merge of PR #36 and request to continue on 14 September 2026. Accounting formulas remain unchanged.

## Calendar contract and required backend alignment

Use the User's configured IANA time zone to derive today from one captured instant for each request/render. Compare date-only values as calendar dates; do not parse them into the browser's local midnight. Missing or invalid zone/date inputs produce an unavailable date status, with Retry/review guidance, not a guessed overdue label.

**Alignment implemented with this increment:** `BillingService.generateForSubscription` previously used `LocalDate.now()` without `UserTimeZoneProvider`. A user-zone label could therefore disagree with whether the button actually generates records. The service now uses the existing User time-zone provider, with fixed-instant boundary verification. This is an explicit alignment of the manual action's day semantics, not merely a copy change. Preserve the catch-up cap, per-subscription lock, uniqueness, user scope and idempotency from ADR-0019.

## Truth table A — billing generation

First matching row wins. `nextBillingDate` is a generation cursor, not evidence of an unpaid provider charge.

| Inputs | Presented state | Action |
|---|---|---|
| Subscription read/date/zone unavailable | Billing status unavailable | Retry or review schedule |
| nextBillingDate < today | Billing generation pending | Generate billing |
| nextBillingDate = today | Billing generation due today | Generate billing |
| nextBillingDate > today | Billing generation caught up; show scheduled date | View schedule |

After generating, re-read the cursor. If the catch-up cap leaves it on/before today, retain pending/due status; a successful POST alone does not prove it is caught up. Zero generated records for a shared subscription with no members is not a paid provider expense. Opening the page never invokes generation or advances the cursor.

## Truth table B — generated contribution records

First matching row wins, per Payment Record. `PaymentRecordResponse` exposes billingDate, amount, status, paidDate and transactionId, but no independently agreed contribution due date.

| Inputs | Presented state | Action |
|---|---|---|
| Payment section unavailable, invalid amount, or unknown status | Contributions unavailable | Retry |
| status = PAID | Received, amount and recorded paid date when supplied | View linked income when transactionId exists |
| status = PENDING | Awaiting contribution for the displayed billing period | Record payment or link existing income under the existing contract |
| Successfully loaded section has no records for selected period | No contribution records for this period | Generate billing if table A permits; add members if required |

**Product decision:** do not call a PENDING contribution overdue merely because billingDate is in the past. A contribution due-date/grace policy is not recorded in the current contract. If overdue contribution labels are desired, define and approve that policy before adding them. A missing transaction link does not make a PAID record unpaid.

The contribution interpretation applies to records with a Subscription Member. Personal subscriptions generate a record with `memberId=null` for the full price (`BillingService.generatePeriod`); label its stored status as a payment record, not money received from another person. Do not count these records in member contribution totals. Keep that distinction from the record itself even if the subscription type has since changed. Marking a Payment Record PAID only updates its status and paid date (`PaymentRecordService.recordPayment`); it creates no Transaction. A linked INGRESS identifies recorded income, never the provider's expense.

For an existing period, aggregate its stored Payment Record amounts and statuses; do not recompute historical amounts from today's subscription price or membership. Expected contribution totals based on current membership must be identified as current expectations, not historical billed facts. Current owner share is distinct from gross provider cost. A record marked PAID does not establish that the provider expense was recorded or paid.

## Truth table C — confirmed statement payment

First matching row wins per statement. Use the backend's `outstandingBalance` (officialBalance minus allocated paidAmount), not current signed credit balance, available credit, minimum payment, or avoid-interest amount. Those are separate labeled figures.

| Inputs | Presented state | Action |
|---|---|---|
| Statement read failed, confirmation metadata malformed, or confirmed outstanding amount unavailable | Statement payment status unavailable | Retry/review statement |
| Confirmed-statement list is empty; settings read confirms no schedule and estimate returns its documented not-configured response | Set up statement schedule | Open Advanced settings; configure schedule or confirm a statement |
| Confirmed-statement list read succeeds with no applicable statement; separate estimate read succeeds | Estimated statement, not confirmed | Review/confirm statement |
| Confirmed, outstandingBalance <= 0 | Statement payment covered | View statement/history |
| Confirmed, outstandingBalance > 0, due date/zone invalid | Outstanding statement payment; due date unavailable | Review statement |
| Confirmed, outstandingBalance > 0, dueDate < today | Outstanding statement payment — past due date | Record card payment via Transfer or review |
| Confirmed, outstandingBalance > 0, dueDate = today | Outstanding statement payment — due today | Record card payment via Transfer or review |
| Confirmed, outstandingBalance > 0, dueDate > today | Outstanding statement payment — upcoming | View due date; optional Record card payment |

“Past due date” describes the remaining confirmed balance and its date; it does not infer bank delinquency, interest, fees, or whether a minimum payment satisfied the bank's requirements. Confirmed minimum and avoid-interest amounts remain available separately. A refund INGRESS is not a card-payment Transfer.

`reconciliationMismatch` is an independent review notice: do not replace the payment state or declare the official snapshot wrong. Positive current credit balance can coexist with an unpaid historical statement. Neither figure is subtracted twice from Net Balance.

## Combined presentation and precedence

Keep each dimension labeled. Do not compress generation, contributions and provider payment into one “paid/unpaid” badge. For a single attention slot, show unavailable essential data first; otherwise past-due confirmed statements, due-today confirmed statements, pending generation, then future obligations. Preserve secondary facts as separate rows and links, not discarded statuses. Within equal-priority statements, sort by due date then stable ID. Contribution PENDING is its own expected-receipt row and never increases recorded available money.
[D5](planning-horizon.md), approved 20 September 2026, permits an explicitly selected,
dated receipt only in a separately labelled hypothetical preview. It changes neither
this dashboard rule nor any recorded balance.

## Boundary verification

At `2026-09-08T04:30:00Z`, today is September 7 in America/Mexico_City and September 8 in Asia/Tokyo. A September 7 generation cursor is due today in the former and pending in the latter; the manual action must use the same day. A confirmed unpaid statement due September 8 is upcoming in the former and due today in the latter. At an instant mapping to September 9 it is past due; an outstanding balance of zero remains covered on every date. A PENDING contribution stays awaiting contribution in all three cases.

Cover yesterday/today/tomorrow, missing dates, invalid zone, PAID/PENDING, partial contributions, no members, personal/shared subscriptions, owner participation on/off, cap-limited catch-up, positive credit with unpaid statement, partial payments and independent mismatch. Assert page reads issue no financial writes and repeated manual generation creates no duplicate records.

## Source anchors

- ADR-0019: per-subscription manual generation, catch-up and idempotency.
- ADR-0022: confirmed statement snapshots and Transfer payment allocation.
- `backend/src/main/java/com/keenti/finances/application/service/BillingService.java`.
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/PaymentRecordResponse.java`.
- `backend/src/main/java/com/keenti/finances/infrastructure/adapter/in/rest/FinancialAccountResource.java`, `toResponse`: outstandingBalance and mismatch are distinct.

Implementation uses the shared derivation in 1D before consuming it in 3A/3B. Contributions remain “awaiting” until a separate due-date policy exists.
