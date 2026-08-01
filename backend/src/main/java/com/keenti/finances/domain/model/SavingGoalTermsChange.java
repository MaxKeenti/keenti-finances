package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Nullable fields are merged with the currently effective Saving Goal terms. */
public record SavingGoalTermsChange(
    BigDecimal targetAmount,
    LocalDate targetDate,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal regularCommitment
) {}
