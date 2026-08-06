package com.keenti.finances.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/** A neutral, atomic movement between two Financial Accounts. */
public record FinancialAccountTransfer(
    Long id,
    Long sourceAccountId,
    Long destinationAccountId,
    BigDecimal amount,
    LocalDate transferDate,
    String notes,
    LocalDateTime createdAt
) {}
