package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SpendingBudgetPeriodResponse(
    Long id,
    Long revisionId,
    LocalDate periodStart,
    LocalDate periodEnd,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal netProgress,
    BigDecimal deposits,
    BigDecimal withdrawals,
    BigDecimal transfersIn,
    BigDecimal transfersOut,
    BigDecimal fundedSpending,
    BigDecimal suggestedTopUp,
    LocalDateTime evaluatedAt
) {}
