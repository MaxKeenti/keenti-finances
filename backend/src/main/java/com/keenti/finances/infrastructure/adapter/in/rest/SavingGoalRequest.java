package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PlanCadence;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.math.BigDecimal;
import java.time.LocalDate;

public record SavingGoalRequest(
    @DecimalMin(value = "0", inclusive = false)
    @Digits(integer = 10, fraction = 2) BigDecimal targetAmount,
    LocalDate targetDate,
    PlanCadence cadence,
    @Min(1) @Max(7) Integer anchorWeekday,
    @Min(1) @Max(31) Integer anchorDayOfMonth,
    @DecimalMin("0") @Digits(integer = 10, fraction = 2)
    BigDecimal regularCommitment
) {}
