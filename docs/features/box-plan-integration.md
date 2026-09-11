# Box Plan presentation and integration

Phase 2 simplifies setup without changing ADR-0021. A Saving Goal asks for a target balance, date, and how often to save. A Spending Budget asks for a desired balance and frequency. Schedule anchors and an optional saving amount are under customization. Suggestions never deposit money; creation, revision, ending, and funding remain explicit user actions.

## Schedule contract for a future planning horizon

`BoxPlanSummary` supplies id, boxId, type, status, creation/closure timestamps and completionAmount. The active statuses are ACTIVE, READY_TO_COMPLETE and OVERDUE; completed/abandoned/ended plans remain historical. At most one plan per Box is active.

The detail contract supplies DAILY/WEEKLY/BIWEEKLY/MONTHLY cadence, an ISO weekday anchor (1–7) for weekly/biweekly schedules, or a day-of-month anchor (1–31) for monthly schedules. Calendar boundaries use the User time zone. Consumers must use the returned period boundaries, not reconstruct them by dividing a date range by 7 or 30.

Saving Goal detail supplies targetAmount, targetDate, boxBalance, remainingAmount, regularCommitment, arrears, currentCommitment, projectedCompletionDate, suggestedExtensionDate, currentPeriod and period/revision history. Saving periods expose startDate/endDate, opening/closing balance, netProgress, requiredAmount and outcome. The creation preview is approximate and does not account for anchors; the saved backend plan and its revision preview are authoritative.

Spending Budget detail supplies desiredBalance, boxBalance, suggestedTopUp and currentPeriod/history. Budget periods expose periodStart/periodEnd, deposits, withdrawals, transfers and fundedSpending. The top-up is max(desiredBalance − current Box balance, 0); it is guidance, not an additional expense.

Revision preview supplies effectiveFrom and proposed terms. Apply uses the existing revision API; terms begin with the next unopened period. Closed period terms remain unchanged. Reads may lazily evaluate periods idempotently; they never move money or complete a plan. Reaching a Saving Goal target requires user-confirmed completion. Ending a plan does not archive its Box.

Phase 5 must consume these authoritative fields and separately approve its formula under D5. It must avoid counting Box reserves, plan suggestions, and recorded expenses as independent cash outflows. This document does not introduce a forecast or change Available to Spend.

Plan-list HTTP 404 is an unavailable section, not an empty list: the existing list API returns 200 with `[]` for an owned Box with no plans. Setup is offered only after that list loads. A historical plan retains the header setup action when no active plan exists; a never-planned Box has its single setup action in the empty state. On narrow screens the dialog actions scroll after the guidance instead of covering it with a sticky footer.
