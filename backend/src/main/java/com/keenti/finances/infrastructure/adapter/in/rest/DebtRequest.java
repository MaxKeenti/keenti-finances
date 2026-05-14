package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;

public record DebtRequest(
    @NotNull Long contactId,
    @NotBlank String description,
    @NotNull @Positive BigDecimal totalAmount
) {}
