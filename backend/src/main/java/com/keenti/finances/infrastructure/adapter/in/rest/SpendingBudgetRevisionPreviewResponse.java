package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;

public record SpendingBudgetRevisionPreviewResponse(
    Long planId,
    LocalDate effectiveFrom,
    String cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal desiredBalance,
    BigDecimal currentBalance,
    BigDecimal suggestedTopUp
) {}
