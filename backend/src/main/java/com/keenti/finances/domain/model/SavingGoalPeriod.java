package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavingGoalPeriod(
    Long id,
    Long planId,
    Long revisionId,
    LocalDate periodStart,
    LocalDate periodEndExclusive,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal netProgress,
    BigDecimal regularCommitment,
    BigDecimal openingArrears,
    BigDecimal requiredAmount,
    BigDecimal arrearsCovered,
    BigDecimal regularProgress,
    BigDecimal extraProgress,
    BigDecimal shortfall,
    Status status,
    LocalDateTime evaluatedAt
) {
    public enum Status {
        OPEN,
        ACHIEVED,
        MISSED
    }

    public BoxPlanPeriod schedulePeriod() {
        return new BoxPlanPeriod(
            id, planId, revisionId, periodStart, periodEndExclusive,
            openingBalance, closingBalance, netProgress, evaluatedAt);
    }
}
