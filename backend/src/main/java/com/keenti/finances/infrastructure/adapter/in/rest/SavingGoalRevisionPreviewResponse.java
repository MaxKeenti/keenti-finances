package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingGoalRevisionPreviewResponse(
    LocalDate effectiveFrom,
    BigDecimal targetAmount,
    LocalDate targetDate,
    String cadence,
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
