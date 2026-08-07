package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record FinancialAccountAppearanceRequest(
    @NotNull @Min(0) @Max(359) Integer hue
) {}
