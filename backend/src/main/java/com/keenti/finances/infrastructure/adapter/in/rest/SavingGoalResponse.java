package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public record SavingGoalResponse(
    Long id,
    Long boxId,
    String type,
    String status,
    BigDecimal targetAmount,
    LocalDate targetDate,
    String cadence,
    Integer anchorWeekday,
    Integer anchorDayOfMonth,
    BigDecimal regularCommitment,
    BigDecimal boxBalance,
    BigDecimal remainingAmount,
    BigDecimal progressPercent,
    BigDecimal arrears,
    BigDecimal currentCommitment,
    LocalDate projectedCompletionDate,
    LocalDate suggestedExtensionDate,
    SavingGoalPeriodResponse currentPeriod,
    List<SavingGoalPeriodResponse> periods,
    List<SavingGoalRevisionResponse> revisions,
    LocalDateTime createdAt,
    LocalDateTime updatedAt,
    LocalDateTime closedAt,
    BigDecimal completionAmount
) {}
