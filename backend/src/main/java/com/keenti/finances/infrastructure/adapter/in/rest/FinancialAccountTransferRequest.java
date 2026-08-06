package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FinancialAccountTransferRequest(
    @NotNull Long sourceAccountId,
    @NotNull Long destinationAccountId,
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate transferDate,
    String notes
) {}
