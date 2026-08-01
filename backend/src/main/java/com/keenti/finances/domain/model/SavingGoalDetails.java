package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SavingGoalDetails(
    BoxPlan plan,
    SavingGoalRevision currentRevision,
    BigDecimal boxBalance,
    BigDecimal remainingAmount,
    BigDecimal progressPercent,
    BigDecimal arrears,
    BigDecimal currentCommitment,
    LocalDate projectedCompletionDate,
    LocalDate suggestedExtensionDate,
    SavingGoalPeriod currentPeriod,
    List<SavingGoalPeriod> periods,
    List<SavingGoalRevision> revisions
) {}
