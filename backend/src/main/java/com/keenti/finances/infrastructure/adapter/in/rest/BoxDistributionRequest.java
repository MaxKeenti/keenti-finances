package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public record BoxDistributionRequest(
    @NotNull Long boxId,
    @NotNull
    @DecimalMin(value = "0.00", inclusive = false)
    @Digits(integer = 10, fraction = 2)
    BigDecimal amount
) {}
