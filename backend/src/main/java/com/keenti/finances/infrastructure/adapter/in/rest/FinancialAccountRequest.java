package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import java.util.List;

public record FinancialAccountRequest(
    @NotBlank String name,
    @NotBlank String kind,
    /** Fixed at creation. Defaults to the Primary Currency. */
    @Size(max = 3) String currency,
    @NotNull @Min(0) @Max(359) Integer hue,
    @NotNull BigDecimal openingBalance,
    @Valid CreditAccountSettingsRequest creditSettings,
    List<@Valid OpeningCreditStatementRequest> openingCreditStatements,
    List<@Valid OpeningCreditMsiPlanRequest> openingMsiPlans
) {
    public FinancialAccountRequest(String name, String kind, Integer hue, BigDecimal openingBalance,
                                   CreditAccountSettingsRequest creditSettings,
                                   List<OpeningCreditStatementRequest> openingCreditStatements,
                                   List<OpeningCreditMsiPlanRequest> openingMsiPlans) {
        this(name, kind, null, hue, openingBalance, creditSettings, openingCreditStatements, openingMsiPlans);
    }
}
