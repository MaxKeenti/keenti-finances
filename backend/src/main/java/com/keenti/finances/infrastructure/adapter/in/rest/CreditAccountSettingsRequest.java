package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record CreditAccountSettingsRequest(
    @NotNull @Positive BigDecimal creditLimit,
    @Min(1) @Max(31) int statementClosingDay,
    @Min(1) @Max(31) int paymentDueDay
) {}
