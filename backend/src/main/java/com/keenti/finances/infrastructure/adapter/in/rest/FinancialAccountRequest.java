package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record FinancialAccountRequest(
    @NotBlank String name,
    @NotBlank String kind,
    @NotNull @Min(0) @Max(359) Integer hue,
    @NotNull BigDecimal openingBalance,
    @Valid CreditAccountSettingsRequest creditSettings,
    List<@Valid OpeningCreditStatementRequest> openingCreditStatements,
    List<@Valid OpeningCreditMsiPlanRequest> openingMsiPlans
) {}
