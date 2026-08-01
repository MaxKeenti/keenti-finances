package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SpendingBudgetResponse(
    Long id,
    Long boxId,
    String type,
    String status,
    BigDecimal desiredBalance,
    String cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal boxBalance,
    BigDecimal suggestedTopUp,
    SpendingBudgetPeriodResponse currentPeriod,
    List<SpendingBudgetPeriodResponse> periods,
    List<SpendingBudgetRevisionResponse> revisions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt,
    BigDecimal completionAmount
) {}
