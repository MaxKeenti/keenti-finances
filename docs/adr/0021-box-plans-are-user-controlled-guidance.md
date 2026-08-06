---
status: accepted
---

# Box Plans are prospective, lazily evaluated User guidance

A Box has at most one active Box Plan: a Saving Goal or a Spending Budget. Plans calculate progress and suggestions but never move money. Finished plans remain historical so a Box can change purpose over time without losing its financial history.

Archiving a Box does not implicitly close its plan. The User must first confirm completion or abandonment of a Saving Goal, or end a Spending Budget, so the plan history records the decision that actually occurred.

A Saving Goal targets a total Box balance by a date. Its daily, weekly, biweekly, or monthly Mini Goals use a configurable cadence anchor and a stable regular commitment. A period succeeds based on net Box growth, not gross deposits, so spending and withdrawals correctly reduce saving progress. Shortfalls become arrears on the next period; once arrears are covered, later periods return to the regular commitment. Extra saving makes the main goal early without lowering that commitment.

Periods are evaluated lazily when the goal is read. No scheduler or automatic closure action is required. This matches the deployment lesson in ADR-0019: date-driven state can catch up idempotently inside a User request without depending on a Railway process waking at a particular time.

Reaching the target produces a **Ready to complete** state rather than completing automatically. The User confirms completion, after which later spending does not erase the achieved plan. An unfinished goal at its target date becomes overdue and previews a suggested extension based on the regular commitment; the User must apply any revision.

A Spending Budget instead recommends a recurring desired Box balance. Funds roll forward, and each period's suggested top-up is `max(period amount - current Box balance, 0)`. It reports funded, spent, and remaining amounts rather than achieved/missed status.

All schedule changes are prospective. A preview recalculates future periods, and applying it creates a new effective plan revision beginning with the next unopened period. Closed periods retain the terms that actually governed them. Corrections to backdated financial events may recalculate actual results and arrears, but never rewrite the historical plan terms.

Funding Triggers are also guidance. A matching INGRESS Category may suggest plan-derived, fixed, or percentage deposits, but the User must confirm and may edit or dismiss every suggestion.

## Consequences

- Plan revisions and evaluated periods must preserve both historical terms and actual outcomes.
- Cadence boundaries use the User's time zone plus a configurable weekday or day-of-month anchor.
- One active plan avoids contradictory success signals between saving growth and recurring spending.
- The first release needs only in-app state badges and calendar highlights; external notifications are not part of the model.
- Lazy evaluation must be idempotent across repeated reads and capable of catching up several elapsed periods.

## Considered options

- **Run Saving Goal and Spending Budget simultaneously on one Box:** rejected because spending can be healthy for a budget while making a saving goal fail.
- **Reduce later commitments after an overfunded period:** rejected because the regular amount is the User's stable commitment; extra saving means the overall goal is ahead.
- **Rewrite past periods after schedule edits:** rejected because it would change the contract that actually applied at the time.
- **Use a background scheduler to close periods:** rejected because evaluation is deterministic on read and scheduled execution is operationally unreliable in the current deployment topology.
- **Automatically apply Funding Triggers or top-ups:** rejected because every movement of allocated money remains a User decision.
