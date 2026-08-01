package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record SpendingBudgetPeriod(
    BoxPlanPeriod planPeriod,
    BigDecimal deposits,
    BigDecimal withdrawals,
    BigDecimal transfersIn,
    BigDecimal transfersOut,
    BigDecimal fundedSpending,
    BigDecimal suggestedTopUp
) {}
