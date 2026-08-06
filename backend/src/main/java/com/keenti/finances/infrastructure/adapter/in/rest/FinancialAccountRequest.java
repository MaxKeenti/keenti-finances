package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FinancialAccountRequest(
    @NotBlank String name,
    @NotBlank String kind,
    @NotNull BigDecimal openingBalance
) {}
