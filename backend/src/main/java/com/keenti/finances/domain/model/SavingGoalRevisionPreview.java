package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingGoalRevisionPreview(
    LocalDate effectiveFrom,
    BigDecimal targetAmount,
    LocalDate targetDate,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal regularCommitment,
    int remainingPeriods,
    BigDecimal boxBalance,
    BigDecimal remainingAmount,
    BigDecimal currentArrears,
    LocalDate projectedCompletionDate,
    LocalDate suggestedExtensionDate
) {}
