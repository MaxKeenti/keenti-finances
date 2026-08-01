package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record SpendingBudgetTerms(
    BigDecimal desiredBalance,
    PlanCadence cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth
) {}
