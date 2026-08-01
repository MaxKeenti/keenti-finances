package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavingGoalRevisionResponse(
    Long id,
    LocalDate effectiveFrom,
    String cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal targetAmount,
    LocalDate targetDate,
    BigDecimal regularCommitment,
    LocalDateTime createdAt,
    LocalDateTime supersededAt,
    boolean scheduled
) {}
