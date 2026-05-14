package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record DebtPaymentResponse(
    Long id,
    Long debtId,
    BigDecimal amount,
    LocalDate paymentDate,
    Long transactionId,
    String notes,
    LocalDateTime createdAt
) {}
