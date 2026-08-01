package com.keenti.finances.infrastructure.adapter.in.rest;

import com.keenti.finances.domain.model.PlanCadence;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record SpendingBudgetTermsRequest(
    @Positive @Digits(integer = 10, fraction = 2) BigDecimal desiredBalance,
    PlanCadence cadence,
    @Min(1) @Max(7) Integer anchorWeekday,
    @Min(1) @Max(31) Integer anchorDayOfMonth
) {}
