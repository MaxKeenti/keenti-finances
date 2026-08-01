package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record SavingGoalPeriodResponse(
    Long id,
    Long revisionId,
    LocalDate startDate,
    LocalDate endDate,
    BigDecimal openingBalance,
    BigDecimal closingBalance,
    BigDecimal netProgress,
    BigDecimal regularCommitment,
    BigDecimal openingArrears,
    BigDecimal requiredAmount,
    BigDecimal arrearsCovered,
    BigDecimal regularProgress,
    BigDecimal extraProgress,
    BigDecimal shortfall,
    String status,
    LocalDateTime evaluatedAt
) {}
