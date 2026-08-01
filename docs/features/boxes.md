# Boxes, Saving Goals, and Spending Budgets

## Outcome

Boxes let a User reserve part of their existing Net Balance for a purpose while preserving Transactions as the only record of money actually entering or leaving. The feature adds allocation, saving guidance, and recurring spending organization without pretending that an internal envelope is a bank account or a Transaction.

## Balance model

At all times:

```text
Net Balance       = all-time INGRESS - all-time EGRESS
In Boxes          = sum of active Box balances
Available to Spend = Net Balance - In Boxes
```

- Box Movements never change Net Balance.
- A Box balance can never be negative.
- Available to Spend may be negative after a real EGRESS Transaction. The Transaction is accepted because the app must reflect what happened.
- A negative Available to Spend is an unreconciled state. The app keeps Transaction entry and corrective actions available, but blocks new Box deposits. The User can reconcile by withdrawing from Boxes, recording missing INGRESS, or correcting records.
- A Box-funded expense reduces Net Balance and the Box by the same amount, so it leaves Available to Spend unchanged. Only the portion funded from Available to Spend reduces it.

## Box lifecycle and personalization

A Box has:

- a required name and hue;
- an optional icon or emoji and description;
- a User-defined display order;
- a non-negative current balance;
- a dated history;
- zero or one active Box Plan.

Active Box names are unique per User. Archived Boxes release their name for reuse. Before archiving, a User must withdraw the full balance and explicitly complete or abandon a Saving Goal, or end a Spending Budget. Archiving never closes a plan on the User's behalf. It preserves history and Transaction links and makes the Box read-only; restore it before making a correction that would change its historical balance.

## Allocation workflows

### Deposit and withdrawal

A deposit reserves Available to Spend in a Box. A withdrawal releases Box money back to Available to Spend. Neither is a Transaction.

A deposit is rejected if Available to Spend is insufficient. A withdrawal is rejected if it exceeds the Box balance.

### Direct transfer

A User may transfer directly between two active Boxes. The operation is atomic, changes neither Net Balance nor Available to Spend, and appears as one linked event from both sides.

### INGRESS distribution

After recording an INGRESS Transaction, the User may optionally distribute some or all of it among several Boxes. The Transaction remains a single INGRESS Transaction; each applied distribution is a Box Movement linked to it for traceability.

Once applied, distributions are independent allocations. Editing or deleting the source Transaction does not silently remove Box money that may already have been spent. The app warns about the relationship and allows any resulting negative Available to Spend to be reconciled normally. A removed source appears as such in Box history.

### EGRESS funding

When recording an EGRESS Transaction, the app asks how it was funded. The User may assign exact amounts from multiple Boxes; the unassigned remainder comes from Available to Spend. A Box assignment cannot exceed that Box's balance, but a real Transaction is still accepted when its Available-to-Spend remainder makes Available to Spend negative.

The Transaction retains its Category independently of its funding. A Category may suggest a relevant Box, but the User must confirm because the same Category can represent spending for another purpose or person.

Editing a Transaction and its Box Funding is atomic. Deleting an EGRESS Transaction refunds its Box Funding. Restoring it requires valid funding assignments because the refunded money may have since been used. A linked archived Box must be restored before a correction can change it.

## Dated history and correction

Every Box Movement and Box Funding record has an effective date and an audit timestamp. Users may backdate internal movements but may not future-date them; future intent belongs to a Box Plan.

A correction recalculates affected balances and Mini Goal results while retaining the schedule that applied at the time. It is rejected if it would make any affected Box negative at that point or at a later point in its history. Transfer corrections update both Boxes atomically.

## One active plan per Box

A Box may be plain or have one active Saving Goal or Spending Budget. The plans are mutually exclusive because saving growth and recurring consumption have conflicting success rules. Completed, abandoned, or ended plans remain attached as history, and the User may start another plan later without replacing the Box or its money.

Plans only calculate, display, and suggest. They never move money automatically.

## Saving Goal

A Saving Goal defines:

- a target total Box balance;
- a target date;
- a daily, weekly, or monthly cadence;
- a cadence anchor (local midnight, weekday, or day of month);
- a regular saving commitment;
- period-by-period Mini Goals.

The target is the desired total balance, not an additional amount. Remaining amount is `max(target balance - current Box balance, 0)`.

Mini Goal progress is net growth:

```text
period progress = closing Box balance - opening Box balance
```

Deposits increase progress; withdrawals, transfers out, and Box-funded spending reduce it. Periods are evaluated automatically when the plan is viewed, so missed application visits require no background scheduler.

The regular commitment is stable. A missed amount becomes arrears on the next period. Saving enough to cover arrears and the current commitment restores the following period to the normal commitment. Savings above both make the main goal ahead of schedule but do not reduce the next regular commitment.

Reaching the target changes the goal to **Ready to complete**. The User confirms completion; the completed plan is then historical while the Box and saved money remain active. Later spending does not erase the achievement.

If the target date arrives unfinished, the app shows the shortfall and previews an extension based on the regular commitment. Changing any target, date, cadence, anchor, or commitment recalculates the preview. Only the User applies the revision.

Schedule revisions are prospective: they begin with the next unopened period. Closed periods keep the terms that governed them, and the calendar marks when terms changed.

## Spending Budget

A Spending Budget defines a recurring desired Box balance and cadence. It continues until the User ends it and has no completion target.

Money never resets or expires. At the start of a period the app recommends:

```text
suggested top-up = max(period amount - current Box balance, 0)
```

The User may accept, change, or ignore the suggestion. A period shows starting funds, deposits, withdrawals, linked spending, and ending funds. It is not labeled achieved or missed. Only Transactions explicitly funded by the Box consume the Spending Budget; same-Category spending funded elsewhere remains outside it.

## Funding Triggers

A Funding Trigger associates an INGRESS Category with a Box. When a matching INGRESS Transaction is recorded, the app offers a one-click, editable funding suggestion:

- Saving Goal: the current commitment plus arrears;
- Spending Budget: the current suggested top-up;
- plain Box: a configured fixed amount or percentage of the INGRESS amount.

Several triggers may match one Transaction. If their combined suggestions exceed available money, the app highlights the excess and requires the User to reduce the applied amounts. Dismissing a suggestion changes nothing. Removing a trigger stops future suggestions and preserves prior history.

## User interface

- Keep Available to Spend visible in the application shell.
- Show Net Balance, In Boxes, and Available to Spend together on the dashboard.
- Link the In Boxes figure to the Boxes overview.
- Reconcile the overview total exactly to the dashboard's In Boxes figure.
- Show balance, plan progress, history, linked Transactions, and a period calendar on Box detail.
- Highlight due, missed, ready, and overdue Saving Goal states in-app.
- Keep external email or push reminders outside the first release.

## First-release boundaries

- MXN only, consistent with the rest of the application.
- No bank-account representation or synchronization.
- No automatic transfers or scheduled money movement.
- No uploaded Box cover images.
- No external notifications.
- No simultaneous Saving Goal and Spending Budget on one Box.
