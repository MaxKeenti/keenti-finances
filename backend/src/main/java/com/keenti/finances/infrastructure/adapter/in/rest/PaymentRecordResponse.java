package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record PaymentRecordResponse(
    Long id,
    Long subscriptionId,
    Long memberId,
    LocalDate billingDate,
    BigDecimal amount,
    String status,
    LocalDate paidDate,
    Long transactionId,
    LocalDateTime createdAt
) {}
