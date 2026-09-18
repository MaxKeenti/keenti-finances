# Credit Financial Accounts in the interface

## Outcome

A Credit Financial Account carries several true figures at once, and the interface's job is to keep them apart. A card can hold a balance in the User's favor while a confirmed statement from an earlier period is still unpaid; neither figure makes the other wrong, and neither is subtracted from Net Balance twice.

This document covers presentation only. The ledger, the payment allocation, and the statement snapshots are ADR-0022's; the obligation statuses are decision D2's.

## The three separate figures

| Figure | What it is | Relationship to the totals |
|---|---|---|
| Signed account balance | What the card owes (negative) or holds in the User's favor (positive), from recorded activity | Already part of Net Balance |
| Available credit | `credit limit + signed balance`, floored at zero | Limit-derived capacity; part of no total |
| Outstanding statement payment | A confirmed statement's `officialBalance` minus allocated payments | A separate obligation; never subtracted from Net Balance |

Labels follow the signed balance, not the account kind: **Credit debt** (displayed as a magnitude), **Credit in your favor**, **No credit debt**. Without saved credit settings there is no limit to subtract from, so available credit is *unknown* rather than `$0.00` — the latter would claim the User has none left. An unreadable settings read is unavailable, which is a different claim again from unconfigured.

The minimum payment and the amount required to avoid interest stay separately labeled. None of them substitutes for the outstanding payment.

## Confirmed statement payment status

Derived from decision D2's truth table C, against the User's calendar day resolved in their configured IANA time zone from one server-captured instant per page load, reused during hydration. A due date is compared as a calendar date, never parsed into the browser's local midnight.

- No confirmed statements and a verified unconfigured schedule → **set up your statement schedule**, with guidance to Advanced settings.
- Read failed, metadata malformed, or amount unreadable → **status unavailable**, with Retry.
- List loaded with nothing confirmed *and* the separate current-estimate read succeeded → **estimated statement, not confirmed**, shown alongside Keenti's own estimate and an invitation to confirm. If that estimate read failed, nothing is known about the cycle and the status is unavailable instead.
- Confirmed, outstanding ≤ 0 → **covered**.
- Confirmed and outstanding → **past due date**, **due today**, **upcoming**, or **due date unavailable**. An unusable due date withholds the date only: the amount still owed is loaded and shown, because discarding the statement over it would make the card look settled.

"Past due date" describes the remaining confirmed balance and its date. It does not assert bank delinquency, interest, fees, or whether a minimum payment satisfied the bank. `reconciliationMismatch` is an independent review notice shown beside the payment status; it never replaces it and never declares the bank's snapshot wrong.

The dashboard shows the one statement per account that most needs attention, in D2's precedence order, and links to the account. Covered statements produce no payment attention row, but their independent mismatch notices remain visible.

The mismatch notice does not follow that selection at all. It is listed for *every* flagged statement, including ones the precedence order did not choose: it is a review notice about recorded activity, not a payment state, so being paid or outranked is no reason to retract it.

## Page order

1. Position, available credit, and tracking start.
2. The confirmed statement obligation, with the snapshot note and a contextual **Record card payment**.
3. MSI Plans.
4. Activity.
5. Confirmed statements, with **Advanced settings** — credit limit, cycle days, and confirming a statement — collapsed below them.

Setup is occasional and sits under the reading the User does daily.

## Recording a card payment

Paying the card is a Transfer, not an expense. The contextual action opens the existing neutral Transfer workflow on the Accounts page with this Credit Financial Account prefilled as the destination and the outstanding amount prefilled, and says so. The source account is deliberately left empty: only the User knows where the money came from.

A prefill is a suggestion. Every field stays editable, and dismissing the dialog records nothing. The one-use prefill is removed from the URL when opened, so a successful save or reload cannot reopen the same payment prompt. On confirmation the Transfer is allocated oldest-unpaid-statement first by the backend. A refund is an INGRESS Transaction on the card and is not a payment.
