package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingBudgetRevisionPreview(
    Long planId,
    LocalDate effectiveFrom,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal desiredBalance,
    BigDecimal currentBalance,
    BigDecimal suggestedTopUp
) {}
