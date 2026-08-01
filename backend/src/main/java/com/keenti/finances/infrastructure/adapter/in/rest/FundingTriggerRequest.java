package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record FundingTriggerRequest(
    @NotNull Long categoryId,
    @NotBlank String strategy,
    @Digits(integer = 10, fraction = 2) BigDecimal fixedAmount,
    @Digits(integer = 3, fraction = 4) BigDecimal percentage,
    Boolean enabled
) {}
