package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;

/** Remaining MSI obligation already represented by the Account Opening Balance. */
public record OpeningCreditMsiPlanRequest(
    @NotNull BigDecimal remainingAmount,
    @NotNull @Min(1) @Max(60) Integer remainingInstallmentCount,
    @NotNull LocalDate firstInstallmentDate
) {}
