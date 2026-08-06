package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record DebtPaymentRequest(
    @NotNull @Positive BigDecimal amount,
    @NotNull LocalDate paymentDate,
    @NotNull Long categoryId,
    Long accountId,
    String notes
) {}
