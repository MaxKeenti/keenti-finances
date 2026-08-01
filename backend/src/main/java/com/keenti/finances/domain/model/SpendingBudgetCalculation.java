package com.keenti.finances.domain.model;

import java.math.BigDecimal;

public record SpendingBudgetCalculation(
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal netProgress,
    BigDecimal deposits,
    BigDecimal withdrawals,
    BigDecimal transfersIn,
    BigDecimal transfersOut,
    BigDecimal fundedSpending,
    BigDecimal suggestedTopUp
) {}
