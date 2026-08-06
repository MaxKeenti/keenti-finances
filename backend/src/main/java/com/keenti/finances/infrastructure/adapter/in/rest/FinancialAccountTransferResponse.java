package com.keenti.finances.infrastructure.adapter.in.rest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

public record FinancialAccountTransferResponse(
    Long id,
    Long sourceAccountId,
    String sourceAccountName,
    Long destinationAccountId,
    String destinationAccountName,
    BigDecimal amount,
    LocalDate transferDate,
    String notes,
    LocalDateTime createdAt
) {}
