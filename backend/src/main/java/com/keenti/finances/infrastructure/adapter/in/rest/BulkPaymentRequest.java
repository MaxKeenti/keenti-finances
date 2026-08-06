package com.keenti.finances.infrastructure.adapter.in.rest;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record BulkPaymentRequest(
    @NotNull Long contactId,
    @NotNull @Positive BigDecimal totalAmount,
    @NotNull LocalDate paymentDate,
    @NotNull Long categoryId,
    Long accountId,
    String notes
) {}
