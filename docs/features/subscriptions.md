# Subscriptions in the interface

## Outcome

A Subscription carries several true figures at once, and the interface's job is to keep them apart. What the provider charges, what the User currently expects to collect from Subscription Members, what a past period's Payment Records actually say, what the User's own share is, and whether Keenti has generated its records yet are five different facts. Compressing them into one "paid/unpaid" badge would state something about the User's money that no single input supports.

This document covers presentation only. Billing generation is ADR-0019's, the Transaction link is ADR-0010's, and the obligation statuses are decision D2's.

## The separate figures

| Figure | What it is | Provenance |
|---|---|---|
| Current provider price | The Subscription's stored `cost` for one billing period | **Current**. Keenti stores one price, so an earlier period's provider cost is not knowable |
| Expected from members now | Today's price split across today's Subscription Members | **Current expectation**, not a bill |
| Your current share | One splitter's share, or `0.00` in middleman mode | **Current** |
| Billed to members / Received / Awaiting | Sums over the **stored** Payment Records of the selected period | **Historical**, never recomputed |

The split mirrors the backend exactly — `cost / (memberCount + (ownerParticipates ? 1 : 0))`, half-up to cents. Mirroring it means rounding like it: every amount is scaled to integer cents before any arithmetic, because rounding a float quotient is a different operation. 20.15 split two ways is exactly 10.075, which `BigDecimal` rounds up to 10.08 and a float round sends down to 10.07 — and 10.08 is the `shareAmount` billing actually stores. No accounting formula changed with this slice; only the labels, the provenance, and the arithmetic's precision did.

Rounding is surfaced rather than absorbed: 299.00 across three splitters is 99.67 each, which totals 299.01, and the remainder is shown so the parts visibly do not claim to reconstruct the price exactly.

**Expected from members** reads each Member's *stored* `shareAmount`, which is what billing writes into Payment Records. Where a stored share is missing or unusable, the even split stands in for that Member alone and the figure is labeled as carrying an estimate, with the number of substituted shares stated. An invented even split presented as the expectation would claim amounts billing would never write.

Owner Participation is orthogonal to Subscription Type, and each combination gets its own sentence:

- **Personal** — nothing is split; the whole current price is the User's.
- **Shared, Owner participates** — the Owner is one of the splitters.
- **Shared, middleman mode** — the Owner forwards the charge; Members cover the whole price and the Owner's share is `0.00`.
- **Shared, no Members** — nothing is expected. With the Owner out of the split as well, nobody holds a share at all, so the own share is *unallocated* rather than `0.00`, which would claim the User owes nothing for a Subscription they still pay. Both member-less cases offer **Add members**.
- **Member list unreadable** — no split is shown. Not "no Members": a zero split count would understate what the User expects to collect and overstate their own share.

## Period figures come only from stored records

Selecting a period aggregates the Payment Records stored for it. A period billed when the Subscription cost 299.00 and had two Members keeps those amounts after a price change or a new Member; re-deriving from today's figures would silently rewrite the User's history.

Per decision D2's truth table B:

- `PAID` → **received**, with the recorded paid date when supplied. A `PAID` record with no linked Transaction is still received; a missing link is a missing link.
- `PENDING` → **awaiting contribution**. A past billing date does *not* make it overdue: no contribution due date or grace period is agreed anywhere in the contract, so "late" would be an invented policy.
- Unreadable amount or unknown status → **unavailable** for that record. It is excluded from the totals, the exclusion is counted, and the totals are marked **partial** rather than presented as the period's whole story. The record offers no write against itself.
- Every record in the period unreadable → **no totals at all**, shown as `—`. A sum over nothing readable is `0.00`, which would report a period as billed nothing and collected nothing purely because its records were malformed.
- A loaded section with no records for the period → **no records for this period**, which is a fact. An unreadable payments section is **unavailable**, which is not.

A `PERSONAL` Subscription's Payment Record has no Member and carries the price the Owner themselves owes. It is labeled as the User's own share for that period and never enters received or awaiting, which describe money expected *from* someone. Every such record in the period is listed, and one whose amount is unreadable says so instead of showing `0.00`.

## Billing generation is a cursor, not a payment

`nextBillingDate` says whether Keenti still owes itself Payment Records. Per truth table A, against the User's calendar day resolved in their configured IANA time zone from one server-captured instant per render:

- before today → **generation pending**; today → **due today**; after today → **caught up**, showing the scheduled date;
- unreadable date or zone → **status unavailable**, with review/retry guidance and no guessed date.

The detail page states the cursor *only* in this block, in this wording. It previously also carried a "Next billing: 15 Sep" line in the header, which announced a past cursor as an upcoming provider charge — two claims the date does not support, on a day that had already gone.

An unreadable cursor no longer fails the page. The name, price, split and stored records are independent facts; dropping all of them over one bad date turned a missing cursor into a Subscription that would not open. The cursor is withheld — never a rolled-over or substituted day — and reported as unavailable wherever it appears.

None of these states says the provider charged anything or that a charge was paid. Zero generated records for a Shared Subscription with no Members is not a paid provider expense, and the page says so where that case arises. Opening the page never generates and never advances the cursor.

## Provider payment status is not available here

Keenti *does* record expenses; what it does not record is a provider charge tied to a Subscription. Shared Payment Records describe member contributions; personal records have no member and are kept separate. Linking a Transaction is currently restricted to INGRESS by TransactionService (an implementation constraint not recorded in ADR-0010). The nullable Transaction↔Subscription annotation has no provider-charge or billing-period relationship. So the page never states whether the provider was paid, and says only that: *provider payment status is not available here; review recorded expenses in Transactions*. It does not tell the User that Keenti has no record of the charge, which is false and would send them looking for a feature that exists, nor explain the data model at them.

## Three actions, three different things

Each is explained beside the control that performs it, rather than in a block of prose at the top of the page:

- **Generate billing** writes the Payment Records for every period the schedule has reached, up to today. It records no money and is idempotent per period.
- **Record payment** marks a Member's contribution as received without creating income. Link an existing income Transaction when one has already been recorded.
- **Link existing income** attaches an income Transaction the User already recorded to a contribution and marks it received. It creates no Transaction.

In Spanish, generation is **Generar registros**. *Generar cobros* reads as issuing charges to the Members — the one thing the action does not do.

Unlinking, correcting, deleting an unlinked billing period, and the public share link are unchanged.

## Layout: controls sit with what they change

The summary carries the period control and the generation action, because a status the User cannot act on without scrolling past two cards is a status they act on blind:

- The **period selector** sits beside the period figures it selects, and is the page's only one. The Payment Records card below follows the same selection rather than offering a second, competing set of period tabs.
- **Generate billing** sits beside the generation state, and is disabled while a generation is in flight.
- The notes are one line each, next to the figure or action they qualify, instead of a wall of prose above everything.

## List page

The overview's headline is a **current price equivalent**: gross list prices as charged today, with yearly plans counted as a twelfth so the two figures are comparable. It is not cash due in any one month and does not subtract contributions the User collects back.

The figures are computed in exact cents, so the monthly one is precisely a twelfth of the yearly one rather than a separately rounded number that disagrees with it.

A failed Subscriptions read is **unavailable** and shows no total. Before this slice it fell through to an empty list, which the page totalled into a confident `$0.00` monthly commitment. A failed member list likewise reports **member count unavailable** rather than `0`, which would also imply billing has nothing to create. Categories and Contacts report their own availability and take nothing else down with them.

The detail page's **Add members** action links to `/subscriptions?members=<id>`, where member management lives. It opens the member dialog and writes nothing until the User adds someone. Three things it must get right:

- The dialog reads the **live** Subscription by id. Holding the object it opened with froze the member list, so an add or a removal only appeared after closing and reopening.
- The request is honoured **once per id**. The query parameter outlives the dialog and every write reloads the page data, so reopening whenever the dialog happened to be closed reopened it immediately after the User dismissed it — and again on every later reload.
- Add and Remove are disabled while a member write is in flight; `addMember` is not idempotent, and a second submit before the reload lands would ask for the same Member twice.

An unreadable Owner Participation value leaves the split unavailable while preserving the current price. Editing an unreadable generation cursor requires choosing a date; the form never silently replaces it with today.
