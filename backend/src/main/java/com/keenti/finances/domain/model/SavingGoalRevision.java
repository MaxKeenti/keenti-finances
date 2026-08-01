package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavingGoalRevision(
    Long id,
    Long planId,
    LocalDate effectiveFrom,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal targetAmount,
    LocalDate targetDate,
    BigDecimal regularCommitment,
    LocalDateTime createdAt,
    LocalDateTime supersededAt
) {
    public BoxPlanRevision schedule() {
        return new BoxPlanRevision(
            id, planId, effectiveFrom, cadence, anchorWeekday,
            anchorDayOfMonth, createdAt, supersededAt);
    }
}
