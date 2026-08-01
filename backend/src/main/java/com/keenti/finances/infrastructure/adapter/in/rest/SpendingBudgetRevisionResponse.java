package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SpendingBudgetRevisionResponse(
    Long id,
    LocalDate effectiveFrom,
    String cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal desiredBalance,
    LocalDateTime createdAt,
    LocalDateTime supersededAt,
    boolean scheduled
) {}
